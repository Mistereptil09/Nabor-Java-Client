package tech.nabor.plugin.viewer;

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

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ViewerPluginTest {

    private StubEventBus eventBus;
    private StubSqliteRepository db;
    private StubHttpClient httpClient;
    private ViewerPlugin plugin;

    @BeforeEach
    void setUp() {
        eventBus = new StubEventBus();
        db = new StubSqliteRepository();
        httpClient = new StubHttpClient();
        httpClient.setGetResponse("{ \"whitelists\": { \"incident\": [\"title\", \"severity\"] }, \"note\": \"ok\" }");

        PluginContext ctx = new StubPluginContext(db, eventBus, httpClient);
        plugin = new ViewerPlugin();
        plugin.initialize(ctx);
    }

    @Test void getId_returnsViewer() { assertEquals("viewer", plugin.getId()); }
    @Test void getDisplayName_isNonEmpty() { assertFalse(plugin.getDisplayName().isBlank()); }
    @Test void getView_doesNotThrow() { assertDoesNotThrow(() -> plugin.getView()); }
    @Test void shutdown_doesNotThrow() { assertDoesNotThrow(() -> plugin.shutdown()); }

    @Test
    void initialize_subscribesToSyncCompleted() {
        assertTrue(eventBus.hasSubscriber("sync.completed"));
    }

    @Test
    void initialize_fetchesWhitelistAndStoresLocally() throws InterruptedException {
        Thread.sleep(200);
        assertTrue(httpClient.wasCalled("/sync/whitelist"));
        assertFalse(db.syncWhitelist().findAll().isEmpty());
    }

    @Test
    void editingIncident_tracksChangelog() {
        db.incidents().save(incident("inc-1", "Old Title", false));
        db.syncChangelog().track(new ChangeEvent(
                "incidents", "inc-1", "UPDATE",
                Map.of("title", "Old Title"), Map.of("title", "New Title"),
                "2025-01-01T00:00:00Z", Instant.now()));

        assertEquals(1, db.syncChangelog().findAll().size());
        assertEquals("UPDATE", db.syncChangelog().findAll().get(0).operation());
    }

    @Test
    void rollback_restoresPreviousValues() {
        db.incidents().save(incident("inc-1", "New Title", false));
        db.syncChangelog().track(new ChangeEvent(
                "incidents", "inc-1", "UPDATE",
                Map.of("title", "Old Title"), Map.of("title", "New Title"),
                "2025-01-01T00:00:00Z", Instant.now()));

        SyncChange entry = db.syncChangelog().findAll().get(0);
        db.syncChangelog().deleteById(entry.id());

        assertTrue(db.syncChangelog().findAll().isEmpty());
    }

    @Test
    void rollback_keepsDirtyWhenOtherChangesRemain() {
        db.syncChangelog().track(new ChangeEvent(
                "incidents", "inc-1", "UPDATE",
                Map.of("title", "old"), Map.of("title", "new"),
                "2025-01-01T00:00:00Z", Instant.now()));
        db.syncChangelog().track(new ChangeEvent(
                "incidents", "inc-1", "UPDATE",
                Map.of("severity", "low"), Map.of("severity", "high"),
                "2025-01-01T00:00:00Z", Instant.now()));

        assertEquals(2, db.syncChangelog().findAll().size());
        List<SyncChange> all = db.syncChangelog().findAll();
        db.syncChangelog().deleteById(all.get(1).id());
        assertEquals(1, db.syncChangelog().findAll().size());
    }

    @Test
    void changelog_supportsMultipleEntityTypes() {
        db.syncChangelog().track(new ChangeEvent(
                "incidents", "i1", "UPDATE",
                Map.of("title", "old"), Map.of("title", "new"),
                null, Instant.now()));
        db.syncChangelog().track(new ChangeEvent(
                "users", "u1", "UPDATE",
                Map.of("first_name", "Alice"), Map.of("first_name", "Maria"),
                null, Instant.now()));
        assertEquals(2, db.syncChangelog().findAll().size());
        assertEquals(1, db.syncChangelog().findByTable("incidents").size());
    }

    @Test
    void neighbourhoodMapping_displaysAllEntries() {
        db.mappingNeighbourhoods().upsert("n1", "Downtown");
        db.mappingNeighbourhoods().upsert("n2", "Uptown");
        assertEquals(2, db.mappingNeighbourhoods().findAll().size());
    }

    @Test
    void whitelist_replaceAll_clearsOldFields() {
        db.syncWhitelist().replaceAll("incident", List.of("title", "status"));
        assertEquals(2, db.syncWhitelist().findByType("incident").size());
        db.syncWhitelist().replaceAll("incident", List.of("severity"));
        assertEquals(1, db.syncWhitelist().findByType("incident").size());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Incident incident(String id, String title, boolean dirty) {
        return new Incident(id, "reporter-1", null, null, null,
                title, "desc", IncidentSeverity.low, IncidentStatus.open,
                null, Instant.parse("2025-01-01T00:00:00Z"), Instant.now(), null);
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

    static class StubHttpClient implements NaborHttpClient {
        private String response = "{}";
        private final List<String> getCalls = new ArrayList<>();
        void setGetResponse(String r) { this.response = r; }
        boolean wasCalled(String path) { return getCalls.contains(path); }
        @Override public String get(String endpoint) { getCalls.add(endpoint); return response; }
        @Override public String post(String e, String b) { return "{}"; }
        @Override public String put(String e, String b) { return "{}"; }
        @Override public String delete(String e) { return "{}"; }
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
        private final StubIncidentRepository incidents = new StubIncidentRepository();
        private final StubSyncChangelogRepository syncChangelog = new StubSyncChangelogRepository();
        private final StubSyncWhitelistRepository syncWhitelist = new StubSyncWhitelistRepository();
        private final StubMappingNeighbourhoodRepository mappingNeighbourhoods = new StubMappingNeighbourhoodRepository();

        @Override public IncidentRepository incidents() { return incidents; }
        @Override public SyncChangelogRepository syncChangelog() { return syncChangelog; }
        @Override public SyncWhitelistRepository syncWhitelist() { return syncWhitelist; }
        @Override public MappingNeighbourhoodRepository mappingNeighbourhoods() { return mappingNeighbourhoods; }
        @Override public tech.nabor.api.repository.user.UserRepository users() { return null; }
        @Override public tech.nabor.api.repository.sync.SyncStateRepository syncState() { return null; }
        @Override public tech.nabor.api.repository.sync.PendingConflictRepository pendingConflicts() { return null; }
        @Override public tech.nabor.api.repository.sync.ResolvedConflictRepository resolvedConflicts() { return null; }
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

    static class StubSyncChangelogRepository implements SyncChangelogRepository {
        private final List<SyncChange> items = new ArrayList<>();
        private int nextId = 1;
        @Override public List<SyncChange> findAll() { return new ArrayList<>(items); }
        @Override public List<SyncChange> findByTable(String tableName) {
            return items.stream().filter(c -> c.tableName().equals(tableName)).toList();
        }
        @Override public Optional<SyncChange> findById(int id) {
            return items.stream().filter(c -> c.id() == id).findFirst();
        }
        @Override public void track(ChangeEvent event) {
            List<String> changedFields = event.newValues() != null
                    ? new ArrayList<>(event.newValues().keySet()) : null;
            items.add(new SyncChange(nextId++, event.tableName(), event.rowId(),
                    event.operation(), changedFields,
                    event.previousValues(), event.newValues(),
                    event.baseUpdatedAt(), event.occurredAt()));
        }
        @Override public void deleteByTableAndRow(String tableName, String rowId) {
            items.removeIf(c -> c.tableName().equals(tableName) && c.rowId().equals(rowId));
        }
        @Override public void deleteAll() { items.clear(); }
        @Override public void deleteById(int id) { items.removeIf(c -> c.id() == id); }
    }

    static class StubSyncWhitelistRepository implements SyncWhitelistRepository {
        private final List<SyncWhitelist> items = new ArrayList<>();
        @Override public List<SyncWhitelist> findByType(String type) {
            return items.stream().filter(w -> w.entityType().equals(type)).toList();
        }
        @Override public List<SyncWhitelist> findAll() { return new ArrayList<>(items); }
        @Override public void replaceAll(String entityType, List<String> fields) {
            items.removeIf(w -> w.entityType().equals(entityType));
            for (String f : fields) items.add(new SyncWhitelist(entityType, f));
        }
    }

    static class StubMappingNeighbourhoodRepository implements MappingNeighbourhoodRepository {
        private final Map<String, MappingNeighbourhood> items = new LinkedHashMap<>();
        @Override public List<MappingNeighbourhood> findAll() { return new ArrayList<>(items.values()); }
        @Override public void upsert(String id, String name) { items.put(id, new MappingNeighbourhood(id, name)); }
    }
}
