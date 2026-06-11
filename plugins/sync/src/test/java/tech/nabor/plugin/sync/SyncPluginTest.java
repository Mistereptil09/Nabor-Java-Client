package tech.nabor.plugin.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.*;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.event.ChangeEvent;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.*;
import tech.nabor.api.repository.incidents.IncidentRepository;
import tech.nabor.api.repository.sync.*;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SyncPluginTest {

    private StubEventBus eventBus;
    private StubSqliteRepository db;
    private StubNaborHttpClient httpClient;
    private SyncPlugin plugin;
    private PullEngine pullEngine;
    private PushEngine pushEngine;

    @BeforeEach
    void setUp() {
        eventBus = new StubEventBus();
        db = new StubSqliteRepository();
        httpClient = new StubNaborHttpClient();

        PluginContext ctx = new StubPluginContext(db, eventBus, httpClient);
        plugin = new SyncPlugin();
        plugin.initialize(ctx);
        pullEngine = new PullEngine(ctx);
        pushEngine = new PushEngine(ctx);
    }

    @Test void getId_returnsSync() { assertEquals("sync", plugin.getId()); }
    @Test void getDisplayName_isNonEmpty() { assertFalse(plugin.getDisplayName().isBlank()); }
    @Test void getView_doesNotThrow() { assertDoesNotThrow(() -> plugin.getView()); }

    @Test
    void initialize_subscribesToEvents() {
        assertTrue(eventBus.hasSubscriber("sync.start"));
        assertTrue(eventBus.hasSubscriber("sync.full"));
        assertTrue(eventBus.hasSubscriber("sync.push"));
    }

    // ── Pull: server overwrites local ──────────────────────────────────────

    @Test
    void pull_overwritesLocalIncident() throws Exception {
        db.incidents().save(incident("inc-1", "Ancien titre"));
        httpClient.setGetResponse(snapshotWithIncident("Nouveau titre distant", "high", "in_progress"));

        pullEngine.pull();

        Incident updated = db.incidents().findById("inc-1").orElseThrow();
        assertEquals("Nouveau titre distant", updated.title());
        assertEquals("high", updated.severity().name());
    }

    @Test
    void pull_insertsNewEntities() throws Exception {
        httpClient.setGetResponse(snapshotWithIncident("Titre", "medium", "open"));

        pullEngine.pullFromEpoch();

        assertTrue(db.incidents().findById("inc-1").isPresent());
    }

    // ── Push: per-entity cleanup ───────────────────────────────────────────

    @Test
    void push_applied_deletesFromOutbox() throws Exception {
        db.syncChangelog().track(new ChangeEvent(
                "incidents", "inc-1", "UPDATE",
                Map.of("title", "old"), Map.of("title", "new"),
                "2025-01-01T00:00:00Z", Instant.now()));
        httpClient.setPostResponse("""
        {"success":true,"has_conflicts":false,"applied_count":1,"conflict_count":0,
         "results":[{"entity_type":"incident","entity_id":"inc-1","status":"applied"}]}""");
        httpClient.setGetResponse(emptySnapshot());

        pushEngine.push();

        assertTrue(db.syncChangelog().findAll().isEmpty());
    }

    @Test
    void push_conflict_keepsInOutbox() throws Exception {
        db.syncChangelog().track(new ChangeEvent(
                "incidents", "inc-1", "UPDATE",
                Map.of("title", "old"), Map.of("title", "new"),
                "2025-01-01T00:00:00Z", Instant.now()));
        httpClient.setPostResponse("""
        {"success":false,"has_conflicts":true,"applied_count":0,"conflict_count":1,
         "results":[{"entity_type":"incident","entity_id":"inc-1","status":"conflict",
          "conflict":{"field_name":"title",
           "client_data":{"title":"new"},"server_data":{"title":"server version"}}}]}""");

        pushEngine.push();

        assertEquals(1, db.syncChangelog().findAll().size());
        assertTrue(db.pendingConflicts().hasConflicts());
    }

    @Test
    void push_emptyOutbox_returnsFalse() throws Exception {
        assertFalse(pushEngine.push());
    }

    // ── Entity type mapping ────────────────────────────────────────────────

    @Test
    void toApiEntityType_mapsCorrectly() {
        assertEquals("incident", PushEngine.toApiEntityType("incidents"));
        assertEquals("user", PushEngine.toApiEntityType("users"));
        assertEquals("listing", PushEngine.toApiEntityType("listings"));
        assertEquals("event", PushEngine.toApiEntityType("evenements"));
    }

    @Test
    void toDbTableName_mapsCorrectly() {
        assertEquals("incidents", PushEngine.toDbTableName("incident"));
        assertEquals("users", PushEngine.toDbTableName("user"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private static Incident incident(String id, String title) {
        return new Incident(id, "reporter-1", null, null, null, title, "desc",
                IncidentSeverity.medium, IncidentStatus.open,
                null, Instant.parse("2025-01-01T00:00:00Z"), Instant.now(), null);
    }

    private static String snapshotWithIncident(String title, String severity, String status) {
        return """
        {"sync_at":"2025-06-07T00:00:00Z","has_more":false,"cursor":"Y3Vy",
         "incidents":[{"id":"inc-1","reporterId":"r1","title":"%s","description":"d",
          "severity":"%s","status":"%s","createdAt":"2025-05-01T00:00:00Z","updatedAt":"2025-06-06T00:00:00Z"}],
         "users_raw":[],"listings":[],"events":[],"listing_categories":[],"event_categories":[]}
        """.formatted(title, severity, status);
    }

    private static String emptySnapshot() {
        return """
        {"sync_at":"2025-06-07T00:00:00Z","has_more":false,"cursor":"Y3Vy",
         "incidents":[],"users_raw":[],"listings":[],"events":[],"listing_categories":[],"event_categories":[]}
        """;
    }

    // ── Stubs ────────────────────────────────────────────────────────────────

    static class StubEventBus implements EventBus {
        private final Map<String, java.util.function.Consumer<Object>> handlers = new LinkedHashMap<>();
        @Override public void publish(String event, Object payload) {
            var h = handlers.get(event);
            if (h != null) h.accept(payload);
        }
        @Override public void subscribe(String event, java.util.function.Consumer<Object> handler) {
            handlers.put(event, handler);
        }
        @Override public void unsubscribe(String event, java.util.function.Consumer<Object> handler) {
            handlers.remove(event);
        }
        boolean hasSubscriber(String event) { return handlers.containsKey(event); }
    }

    static class StubNaborHttpClient implements NaborHttpClient {
        private String getResponse = "{}";
        private String postResponse = "{}";
        String lastGetUrl;

        void setGetResponse(String json) { this.getResponse = json; }
        void setPostResponse(String json) { this.postResponse = json; }
        String lastGetUrl() { return lastGetUrl; }

        @Override public String get(String endpoint) throws IOException { lastGetUrl = endpoint; return getResponse; }
        @Override public String post(String endpoint, String body) { return postResponse; }
        @Override public String put(String endpoint, String body) { return "{}"; }
        @Override public String delete(String endpoint) { return "{}"; }
    }

    static class StubPluginContext implements PluginContext {
        private final SqliteRepository db; private final EventBus eb; private final NaborHttpClient http;
        StubPluginContext(SqliteRepository db, EventBus eb, NaborHttpClient http) {
            this.db = db; this.eb = eb; this.http = http;
        }
        @Override public NaborHttpClient getHttpClient() { return http; }
        @Override public SqliteRepository getDb() { return db; }
        @Override public ConnectedUser getConnectedUser() { return null; }
        @Override public I18n getI18n() {
            return new I18n() {
                @Override public String t(String key, Object... args) { return key; }
                @Override public void registerBundle(String name, ClassLoader cl) {}
                @Override public void setLocale(String locale) {}
                @Override public String getLocale() { return "fr"; }
            };
        }
        @Override public EventBus getEventBus() { return eb; }
        @Override public NaborReporter getReporter() {
            return new NaborReporter() {
                @Override public void reportError(tech.nabor.api.error.NaborException e) {}
                @Override public void reportInfo(String m) {}
                @Override public void reportWarning(String m) {}
            };
        }
    }

    static class StubSqliteRepository implements SqliteRepository {
        private final StubSyncStateRepository syncState = new StubSyncStateRepository();
        private final StubSyncChangelogRepository syncChangelog = new StubSyncChangelogRepository();
        private final StubIncidentRepository incidents = new StubIncidentRepository();
        private final StubPendingConflictRepository pendingConflicts = new StubPendingConflictRepository();

        @Override public SyncStateRepository syncState() { return syncState; }
        @Override public SyncChangelogRepository syncChangelog() { return syncChangelog; }
        @Override public IncidentRepository incidents() { return incidents; }
        @Override public PendingConflictRepository pendingConflicts() { return pendingConflicts; }
        @Override public ResolvedConflictRepository resolvedConflicts() { return null; }
        @Override public tech.nabor.api.repository.user.UserRepository users() { return null; }
        @Override public MappingNeighbourhoodRepository mappingNeighbourhoods() {
            return new MappingNeighbourhoodRepository() {
                @Override public List<MappingNeighbourhood> findAll() { return List.of(); }
                @Override public void upsert(String id, String name) {}
            };
        }
        @Override public SyncWhitelistRepository syncWhitelist() { return null; }

        // All other repositories — return null (not used in sync plugin tests)
        @Override public tech.nabor.api.repository.local.LocalAccountRepository localAccounts() { return null; }
        @Override public tech.nabor.api.repository.local.LocaleConfigRepository localeConfigs() { return null; }
        @Override public tech.nabor.api.repository.local.PluginStateRepository pluginStates() { return null; }
        @Override public tech.nabor.api.repository.local.PluginConfigRepository pluginConfigs() { return null; }
        @Override public tech.nabor.api.repository.user.UserSessionRepository userSessions() { return null; }
        @Override public tech.nabor.api.repository.user.UserNotificationPreferencesRepository notificationPreferences() { return null; }
        @Override public tech.nabor.api.repository.social.FollowRepository follows() { return null; }
        @Override public tech.nabor.api.repository.social.FriendshipRepository friendships() { return null; }
        @Override public tech.nabor.api.repository.social.UserBlockRepository userBlocks() { return null; }
        @Override public tech.nabor.api.repository.social.UserSwipeRepository userSwipes() { return null; }
        @Override public tech.nabor.api.repository.messages.ChatGroupRepository chatGroups() { return null; }
        @Override public tech.nabor.api.repository.messages.UserInGroupRepository usersInGroup() { return null; }
        @Override public tech.nabor.api.repository.messages.MessageMetadataRepository messages() { return null; }
        @Override public tech.nabor.api.repository.messages.MessageReadReceiptRepository readReceipts() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingCategoryRepository listingCategories() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingRepository listings() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingTransactionRepository listingTransactions() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingReportRepository listingReports() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingModerationActionRepository listingModerationActions() { return null; }
        @Override public tech.nabor.api.repository.events.EvenementCategoryRepository evenementCategories() { return null; }
        @Override public tech.nabor.api.repository.events.EvenementRepository evenements() { return null; }
        @Override public tech.nabor.api.repository.events.EventParticipantRepository eventParticipants() { return null; }
        @Override public tech.nabor.api.repository.events.EventSwipeRepository eventSwipes() { return null; }
        @Override public tech.nabor.api.repository.events.EventReportRepository eventReports() { return null; }
        @Override public tech.nabor.api.repository.events.EventModerationActionRepository eventModerationActions() { return null; }
        @Override public tech.nabor.api.repository.polls.PollRepository polls() { return null; }
        @Override public tech.nabor.api.repository.polls.PollOptionRepository pollOptions() { return null; }
        @Override public tech.nabor.api.repository.polls.VoteRepository votes() { return null; }
    }

    // ── Repository stubs ────────────────────────────────────────────────────

    static class StubSyncStateRepository implements SyncStateRepository {
        private SyncState state;
        @Override public Optional<SyncState> get() { return Optional.ofNullable(state); }
        @Override public void save(SyncState s) { this.state = s; }
        @Override public void updateLatestCursor(String c) {
            state = new SyncState(c, null, state != null && state.isRollingBack());
        }
        @Override public void updateResumeCursor(String c) {
            state = new SyncState(state != null ? state.latestSyncCursor() : null, c,
                    state != null && state.isRollingBack());
        }
        @Override public void setRollingBack(boolean b) {
            if (state != null) state = new SyncState(state.latestSyncCursor(), state.resumeCursor(), b);
        }
    }

    static class StubSyncChangelogRepository implements SyncChangelogRepository {
        private final List<SyncChange> items = new ArrayList<>();
        private int nextId = 1;
        @Override public List<SyncChange> findAll() { return new ArrayList<>(items); }
        @Override public List<SyncChange> findByTable(String t) {
            return items.stream().filter(c -> c.tableName().equals(t)).toList();
        }
        @Override public Optional<SyncChange> findById(int id) {
            return items.stream().filter(c -> c.id() == id).findFirst();
        }
        @Override public void track(ChangeEvent e) {
            List<String> cf = e.newValues() != null ? new ArrayList<>(e.newValues().keySet()) : null;
            items.add(new SyncChange(nextId++, e.tableName(), e.rowId(), e.operation(),
                    cf, e.previousValues(), e.newValues(), e.baseUpdatedAt(), e.occurredAt()));
        }
        @Override public void deleteByTableAndRow(String t, String r) {
            items.removeIf(c -> c.tableName().equals(t) && c.rowId().equals(r));
        }
        @Override public void deleteAll() { items.clear(); }
        @Override public void deleteById(int id) { items.removeIf(c -> c.id() == id); }
    }

    static class StubIncidentRepository implements IncidentRepository {
        private final Map<String, Incident> items = new LinkedHashMap<>();
        @Override public List<Incident> findAll() { return new ArrayList<>(items.values()); }
        @Override public Optional<Incident> findById(String id) { return Optional.ofNullable(items.get(id)); }
        @Override public List<Incident> findByReporterId(String r) { return List.of(); }
        @Override public List<Incident> findByAssignedTo(String u) { return List.of(); }
        @Override public List<Incident> findByNeighbourhood(String n, int l) { return List.of(); }
        @Override public List<Incident> findByStatus(IncidentStatus s, int l) { return List.of(); }
        @Override public List<Incident> findBySeverity(IncidentSeverity s, int l) { return List.of(); }
        @Override public List<Incident> findOpen(String n, int l) { return List.of(); }
        @Override public void save(Incident i) { items.put(i.id(), i); }
        @Override public void delete(String id) { items.remove(id); }
        @Override public void assign(String id, String userId) {}
        @Override public void resolve(String id) {}
    }

    static class StubPendingConflictRepository implements PendingConflictRepository {
        private final List<PendingConflict> items = new ArrayList<>();
        @Override public List<PendingConflict> findAll() { return new ArrayList<>(items); }
        @Override public List<PendingConflict> findByTable(String t) { return List.of(); }
        @Override public List<PendingConflict> findByRow(String t, String r) { return List.of(); }
        @Override public boolean hasConflicts() { return !items.isEmpty(); }
        @Override public void save(PendingConflict c) { items.add(c); }
        @Override public void delete(int id) { items.removeIf(c -> c.id() == id); }
        @Override public void deleteAll() { items.clear(); }
    }
}
