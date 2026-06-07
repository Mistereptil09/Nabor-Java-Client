package tech.nabor.plugin.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.nabor.api.*;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.api.model.sync.ResolvedConflict;
import tech.nabor.api.model.sync.SyncState;
import tech.nabor.api.repository.incidents.IncidentRepository;
import tech.nabor.api.repository.sync.PendingConflictRepository;
import tech.nabor.api.repository.sync.ResolvedConflictRepository;
import tech.nabor.api.repository.sync.SyncChangelogRepository;
import tech.nabor.api.repository.sync.SyncStateRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SyncPluginTest {

    private StubEventBus eventBus;
    private StubNaborHttpClient httpClient;
    private StubSqliteRepository db;
    private SyncPlugin plugin;

    @BeforeEach
    void setUp() {
        eventBus = new StubEventBus();
        httpClient = new StubNaborHttpClient();
        db = new StubSqliteRepository();

        PluginContext ctx = new StubPluginContext(db, eventBus, httpClient);
        plugin = new SyncPlugin();
        plugin.initialize(ctx);
    }

    // ── Interface contract ──────────────────────────────────────────────────

    @Test
    void getId_returnsSync() {
        assertEquals("sync", plugin.getId());
    }

    @Test
    void getDisplayName_returnsSync() {
        assertEquals("Sync", plugin.getDisplayName());
    }

    @Test
    void getView_returnsEmpty() {
        assertTrue(plugin.getView().isEmpty());
    }

    // ── Event registration ──────────────────────────────────────────────────

    @Test
    void initialize_registersSyncStartListener() {
        assertTrue(eventBus.hasSubscriber("sync.start"));
    }

    @Test
    void onSyncStart_runsSync() {
        db.syncState().save(new SyncState(Instant.parse("2025-06-01T00:00:00Z"), null, false));

        eventBus.publish("sync.start", null);
        sleep(500);

        assertTrue(eventBus.publishedEvents().contains("sync.completed"),
                "sync.completed should be published after successful sync");
    }

    @Test
    void onSyncStart_withFailure_publishesSyncFailed() {
        // Seed a dirty incident so push() actually tries to POST
        db.syncState().save(new SyncState(Instant.parse("2025-06-01T00:00:00Z"), null, false));
        db.incidents().save(dirtyIncident("inc-1", "Test", IncidentStatus.open));
        httpClient.setThrowOnNextPost(true);

        eventBus.publish("sync.start", null);
        sleep(500);

        assertTrue(eventBus.publishedEvents().contains("sync.failed"),
                "sync.failed should be published on error");
    }

    // ── Push: server-side conflict detection ────────────────────────────────

    @Test
    void push_serverReturnsConflict_createsPendingConflict() {
        // Seed a dirty incident — push() will POST it
        db.incidents().save(dirtyIncident("inc-1", "Titre local", IncidentStatus.open));
        db.syncState().save(new SyncState(Instant.parse("2025-06-01T00:00:00Z"), null, false));

        // Simulate the server response: one conflict
        String pushResponse = """
        {
          "success": false,
          "has_conflicts": true,
          "applied_count": 0,
          "conflict_count": 1,
          "results": [
            {
              "entity_type": "incident",
              "entity_id": "inc-1",
              "status": "conflict",
              "conflict": {
                "field_name": "title",
                "client_data": { "title": "Titre local" },
                "server_data": { "id": "inc-1", "title": "Titre serveur",
                  "status": "open", "updatedAt": "2025-06-05T00:00:00Z" }
              }
            }
          ]
        }
        """;
        httpClient.setPostResponse(pushResponse);

        eventBus.publish("sync.start", null);
        sleep(500);

        // Conflict should be recorded for UI resolution
        assertTrue(db.pendingConflicts().hasConflicts(),
                "Push response with conflict should create pending_conflict entry");

        // The incident should still be dirty (not marked clean)
        Optional<Incident> local = db.incidents().findById("inc-1");
        assertTrue(local.isPresent());
        assertTrue(local.get().isDirty(), "Incident should remain dirty after conflict");
    }

    @Test
    void push_serverApplies_setsDirtyToFalse() {
        Incident dirty = dirtyIncident("inc-1", "Titre", IncidentStatus.in_progress);
        db.incidents().save(dirty);
        db.syncState().save(new SyncState(Instant.parse("2025-06-01T00:00:00Z"), null, false));

        // Server accepts the change
        String pushResponse = """
        {
          "success": true,
          "has_conflicts": false,
          "applied_count": 1,
          "conflict_count": 0,
          "sync_at": "2025-06-07T00:00:00Z",
          "results": [
            {
              "entity_type": "incident",
              "entity_id": "inc-1",
              "status": "applied"
            }
          ]
        }
        """;
        httpClient.setPostResponse(pushResponse);

        eventBus.publish("sync.start", null);
        sleep(500);

        Optional<Incident> updated = db.incidents().findById("inc-1");
        assertTrue(updated.isPresent());
        assertFalse(updated.get().isDirty(), "Server-applied incident should be clean");
        assertNotNull(updated.get().syncedAt(), "syncedAt should be set after successful push");
    }

    // ── Pull: applies remote data ───────────────────────────────────────────

    @Test
    void pull_appliesRemoteIncident() {
        // Local has an existing incident (not dirty) — remote has updated title
        Incident local = cleanIncident("inc-1", "Ancien titre", IncidentStatus.open);
        db.incidents().save(local);
        db.syncState().save(new SyncState(Instant.parse("2025-06-01T00:00:00Z"), null, false));

        // API returns camelCase fields
        String snapshotJson = """
        {
          "sync_at": "2025-06-07T00:00:00Z",
          "has_more": false,
          "incidents": [
            {
              "id": "inc-1",
              "reporterId": "reporter-1",
              "title": "Nouveau titre distant",
              "description": "Description mise a jour",
              "severity": "high",
              "status": "in_progress",
              "createdAt": "2025-05-01T00:00:00Z",
              "updatedAt": "2025-06-06T00:00:00Z"
            }
          ],
          "users_raw": [],
          "listings": [],
          "events": [],
          "listing_categories": [],
          "event_categories": []
        }
        """;
        httpClient.setGetResponse(snapshotJson);

        eventBus.publish("sync.start", null);
        sleep(500);

        // Local should be overwritten with remote data
        Optional<Incident> updated = db.incidents().findById("inc-1");
        assertTrue(updated.isPresent());
        assertEquals("Nouveau titre distant", updated.get().title());
        assertEquals("high", updated.get().severity().name());
        assertEquals(IncidentStatus.in_progress, updated.get().status());
        assertEquals("2025-06-06T00:00:00Z", updated.get().baseUpdatedAt());
    }

    @Test
    void pull_doesNotOverwriteDirtyIncident() {
        // Local incident is dirty → should NOT be overwritten by pull
        Incident local = dirtyIncident("inc-1", "Mon titre local", IncidentStatus.open);
        db.incidents().save(local);
        db.syncState().save(new SyncState(Instant.parse("2025-06-01T00:00:00Z"), null, false));

        String snapshotJson = """
        {
          "sync_at": "2025-06-07T00:00:00Z",
          "has_more": false,
          "incidents": [
            {
              "id": "inc-1",
              "reporterId": "reporter-1",
              "title": "Titre distant",
              "description": "Desc distante",
              "severity": "low",
              "status": "resolved",
              "createdAt": "2025-05-01T00:00:00Z",
              "updatedAt": "2025-06-06T00:00:00Z"
            }
          ],
          "users_raw": [],
          "listings": [],
          "events": [],
          "listing_categories": [],
          "event_categories": []
        }
        """;
        httpClient.setGetResponse(snapshotJson);

        eventBus.publish("sync.start", null);
        sleep(500);

        // Local kept because it's dirty
        Optional<Incident> updated = db.incidents().findById("inc-1");
        assertTrue(updated.isPresent());
        assertEquals("Mon titre local", updated.get().title(), "Dirty local should not be overwritten by pull");
    }

    // ── Pull: overlap window ────────────────────────────────────────────────

    @Test
    void pull_usesOverlapWindow() {
        db.syncState().save(new SyncState(
                Instant.parse("2025-06-05T12:00:00Z"), null, false));
        httpClient.setGetResponse("""
        {
          "sync_at": "2025-06-07T00:00:00Z",
          "has_more": false,
          "incidents": [],
          "users_raw": [],
          "listings": [],
          "events": [],
          "listing_categories": [],
          "event_categories": []
        }
        """);

        eventBus.publish("sync.start", null);
        sleep(500);

        String requestedUrl = httpClient.lastGetUrl();
        assertNotNull(requestedUrl);
        assertTrue(requestedUrl.contains("since=2025-06-05T11:59:30Z"),
                "Pull should use overlap window (lastSync - 30s), got: " + requestedUrl);
    }

    // ── Stale conflict auto-cleanup ─────────────────────────────────────────

    @Test
    void pull_autoCleansStaleConflict() {
        // Seed a local incident and a pending conflict
        Incident local = cleanIncident("inc-1", "Titre local", IncidentStatus.open);
        db.incidents().save(local);
        db.syncState().save(new SyncState(Instant.parse("2025-06-01T00:00:00Z"), null, false));

        // Pre-existing conflict: client wanted "Titre A", server had "Titre B"
        db.pendingConflicts().save(new PendingConflict(
                0, "incidents", "inc-1", "title",
                "{\"title\": \"Titre A\"}",
                "{\"title\": \"Titre B\"}",
                Instant.now()));

        // New pull: server now has "Titre A" (client's version won)
        String snapshotJson = """
        {
          "sync_at": "2025-06-07T00:00:00Z",
          "has_more": false,
          "incidents": [
            {
              "id": "inc-1",
              "reporterId": "reporter-1",
              "title": "Titre A",
              "description": "Description",
              "severity": "medium",
              "status": "open",
              "createdAt": "2025-05-01T00:00:00Z",
              "updatedAt": "2025-06-06T00:00:00Z"
            }
          ],
          "users_raw": [],
          "listings": [],
          "events": [],
          "listing_categories": [],
          "event_categories": []
        }
        """;
        httpClient.setGetResponse(snapshotJson);

        eventBus.publish("sync.start", null);
        sleep(500);

        // The stale conflict should be auto-removed
        assertFalse(db.pendingConflicts().hasConflicts(),
                "Stale conflict should be auto-cleaned when server matches one side");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Incident dirtyIncident(String id, String title, IncidentStatus status) {
        return new Incident(
                id, "reporter-1", null, null, null,
                title, "Description", IncidentSeverity.medium, status,
                null, Instant.parse("2025-05-01T00:00:00Z"), Instant.now(), null,
                "2025-05-01T00:00:00Z",  // baseUpdatedAt
                null,                    // syncedAt
                true);                   // isDirty
    }

    private static Incident cleanIncident(String id, String title, IncidentStatus status) {
        return new Incident(
                id, "reporter-1", null, null, null,
                title, "Description", IncidentSeverity.medium, status,
                null, Instant.parse("2025-05-01T00:00:00Z"), Instant.now(), null,
                "2025-05-01T00:00:00Z",
                Instant.parse("2025-05-02T00:00:00Z"),
                false);
    }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── Stubs ────────────────────────────────────────────────────────────────

    static class StubEventBus implements EventBus {
        private final java.util.Map<String, java.util.function.Consumer<Object>> handlers = new java.util.LinkedHashMap<>();
        private final List<String> published = new ArrayList<>();

        @Override public void publish(String event, Object payload) {
            published.add(event);
            var handler = handlers.get(event);
            if (handler != null) handler.accept(payload);
        }
        @Override public void subscribe(String event, java.util.function.Consumer<Object> handler) {
            handlers.put(event, handler);
        }
        @Override public void unsubscribe(String event, java.util.function.Consumer<Object> handler) {
            handlers.remove(event);
        }
        boolean hasSubscriber(String event) { return handlers.containsKey(event); }
        List<String> publishedEvents() { return new ArrayList<>(published); }
    }

    static class StubNaborHttpClient implements NaborHttpClient {
        private String getResponse = "{}";
        private String postResponse = "{}";
        private boolean throwOnPost;
        private String lastGetUrl;

        void setGetResponse(String json) { this.getResponse = json; }
        void setPostResponse(String json) { this.postResponse = json; }
        void setThrowOnNextPost(boolean v) { this.throwOnPost = v; }
        String lastGetUrl() { return lastGetUrl; }

        @Override public String get(String endpoint) throws java.io.IOException {
            lastGetUrl = endpoint;
            return getResponse;
        }
        @Override public String post(String endpoint, String body) throws java.io.IOException {
            if (throwOnPost) throw new java.io.IOException("Forced failure");
            return postResponse;
        }
        @Override public String put(String endpoint, String body) throws java.io.IOException { return "{}"; }
        @Override public String delete(String endpoint) throws java.io.IOException { return "{}"; }
    }

    static class StubPluginContext implements PluginContext {
        private final SqliteRepository db;
        private final EventBus eventBus;
        private final NaborHttpClient http;
        StubPluginContext(SqliteRepository db, EventBus eb, NaborHttpClient http) {
            this.db = db; this.eventBus = eb; this.http = http;
        }
        @Override public NaborHttpClient getHttpClient() { return http; }
        @Override public SqliteRepository getDb() { return db; }
        @Override public ConnectedUser getConnectedUser() { return null; }
        @Override public I18n getI18n() { return null; }
        @Override public EventBus getEventBus() { return eventBus; }
        @Override public NaborReporter getReporter() {
            return new NaborReporter() {
                @Override public void reportError(tech.nabor.api.error.NaborException e) {}
                @Override public void reportInfo(String message) {}
                @Override public void reportWarning(String message) {}
            };
        }
    }

    static class StubSqliteRepository implements SqliteRepository {
        private final StubPendingConflictRepository pendingConflicts = new StubPendingConflictRepository();
        private final StubResolvedConflictRepository resolvedConflicts = new StubResolvedConflictRepository();
        private final StubSyncStateRepository syncState = new StubSyncStateRepository();
        private final StubSyncChangelogRepository syncChangelog = new StubSyncChangelogRepository();
        private final StubIncidentRepository incidents = new StubIncidentRepository();

        @Override public SyncStateRepository syncState() { return syncState; }
        @Override public StubSyncChangelogRepository syncChangelog() { return syncChangelog; }
        @Override public PendingConflictRepository pendingConflicts() { return pendingConflicts; }
        @Override public ResolvedConflictRepository resolvedConflicts() { return resolvedConflicts; }
        @Override public IncidentRepository incidents() { return incidents; }

        @Override public tech.nabor.api.repository.local.LocalAccountRepository localAccounts() { return null; }
        @Override public tech.nabor.api.repository.local.LocaleConfigRepository localeConfigs() { return null; }
        @Override public tech.nabor.api.repository.local.PluginStateRepository pluginStates() { return null; }
        @Override public tech.nabor.api.repository.local.PluginConfigRepository pluginConfigs() { return null; }
        @Override public tech.nabor.api.repository.user.UserRepository users() { return new StubUserRepository(); }
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
        @Override public tech.nabor.api.repository.listings.ListingCategoryRepository listingCategories() { return new StubListingCategoryRepository(); }
        @Override public tech.nabor.api.repository.listings.ListingRepository listings() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingTransactionRepository listingTransactions() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingReportRepository listingReports() { return null; }
        @Override public tech.nabor.api.repository.listings.ListingModerationActionRepository listingModerationActions() { return null; }
        @Override public tech.nabor.api.repository.events.EvenementCategoryRepository evenementCategories() { return new StubEventCategoryRepository(); }
        @Override public tech.nabor.api.repository.events.EvenementRepository evenements() { return null; }
        @Override public tech.nabor.api.repository.events.EventParticipantRepository eventParticipants() { return null; }
        @Override public tech.nabor.api.repository.events.EventSwipeRepository eventSwipes() { return null; }
        @Override public tech.nabor.api.repository.events.EventReportRepository eventReports() { return null; }
        @Override public tech.nabor.api.repository.events.EventModerationActionRepository eventModerationActions() { return null; }
        @Override public tech.nabor.api.repository.polls.PollRepository polls() { return null; }
        @Override public tech.nabor.api.repository.polls.PollOptionRepository pollOptions() { return null; }
        @Override public tech.nabor.api.repository.polls.VoteRepository votes() { return null; }
    }

    static class StubPendingConflictRepository implements PendingConflictRepository {
        private final List<PendingConflict> items = new ArrayList<>();
        @Override public List<PendingConflict> findAll() { return new ArrayList<>(items); }
        @Override public List<PendingConflict> findByTable(String tableName) {
            return items.stream().filter(c -> c.tableName().equals(tableName)).toList();
        }
        @Override public List<PendingConflict> findByRow(String tableName, String rowId) {
            return items.stream()
                    .filter(c -> c.tableName().equals(tableName) && c.rowId().equals(rowId))
                    .toList();
        }
        @Override public boolean hasConflicts() { return !items.isEmpty(); }
        @Override public void save(PendingConflict c) { items.add(c); }
        @Override public void delete(int id) { items.removeIf(c -> c.id() == id); }
        @Override public void deleteAll() { items.clear(); }
    }

    static class StubResolvedConflictRepository implements ResolvedConflictRepository {
        private final List<ResolvedConflict> items = new ArrayList<>();
        @Override public Optional<ResolvedConflict> findPrevious(
                String tableName, String rowId, String fieldName) { return Optional.empty(); }
        @Override public List<ResolvedConflict> findByRow(
                String tableName, String rowId) { return List.of(); }
        @Override public void save(ResolvedConflict c) { items.add(c); }
        @Override public void deleteByRow(String tableName, String rowId) {}
    }

    static class StubSyncStateRepository implements SyncStateRepository {
        private SyncState state;
        @Override public Optional<SyncState> get() { return Optional.ofNullable(state); }
        @Override public void save(SyncState s) { this.state = s; }
        @Override public void updateLastSyncedAt(Instant at) { /* no-op for stub */ }
        @Override public void updateSyncToken(String token) { /* no-op */ }
        @Override public void setRollingBack(boolean rollingBack) { /* no-op */ }
    }

    static class StubSyncChangelogRepository implements SyncChangelogRepository {
        private boolean markAllCalled;

        boolean isMarkAllCalled() { return markAllCalled; }

        @Override public List<tech.nabor.api.model.sync.SyncChange> findUnsynced() { return List.of(); }
        @Override public List<tech.nabor.api.model.sync.SyncChange> findByTable(String tableName) { return List.of(); }
        @Override public void track(tech.nabor.api.event.ChangeEvent event) {}
        @Override public void markSynced(int id) {}
        @Override public void markAllSynced() { markAllCalled = true; }
        @Override public void deleteUnsynced() {}
    }

    static class StubIncidentRepository implements IncidentRepository {
        private final List<Incident> items = new ArrayList<>();
        @Override public Optional<Incident> findById(String id) {
            return items.stream().filter(i -> i.id().equals(id)).findFirst();
        }
        @Override public List<Incident> findByReporterId(String reporterId) { return List.of(); }
        @Override public List<Incident> findByAssignedTo(String userId) { return List.of(); }
        @Override public List<Incident> findByNeighbourhood(String nId, int limit) { return List.of(); }
        @Override public List<Incident> findByStatus(IncidentStatus status, int limit) {
            return items.stream().filter(i -> i.status() == status).limit(limit).toList();
        }
        @Override public List<Incident> findBySeverity(IncidentSeverity severity, int limit) {
            return items.stream().filter(i -> i.severity() == severity).limit(limit).toList();
        }
        @Override public List<Incident> findOpen(String nId, int limit) { return List.of(); }
        @Override public List<Incident> findDirty() {
            return items.stream().filter(Incident::isDirty).toList();
        }
        @Override public void save(Incident incident) {
            items.removeIf(i -> i.id().equals(incident.id()));
            items.add(incident);
        }
        @Override public void assign(String id, String userId) {}
        @Override public void resolve(String id) {}
    }

    static class StubUserRepository implements tech.nabor.api.repository.user.UserRepository {
        private final List<tech.nabor.api.model.user.User> items = new ArrayList<>();
        @Override public Optional<tech.nabor.api.model.user.User> findById(String id) {
            return items.stream().filter(u -> u.id().equals(id)).findFirst();
        }
        @Override public Optional<tech.nabor.api.model.user.User> findByEmail(String email) { return Optional.empty(); }
        @Override public List<tech.nabor.api.model.user.User> findByNeighbourhood(String nId) { return List.of(); }
        @Override public List<tech.nabor.api.model.user.User> findByRole(tech.nabor.api.model.enums.UserRole role) { return List.of(); }
        @Override public void save(tech.nabor.api.model.user.User user) {
            items.removeIf(u -> u.id().equals(user.id()));
            items.add(user);
        }
        @Override public void delete(String id) { items.removeIf(u -> u.id().equals(id)); }
    }

    static class StubListingCategoryRepository implements tech.nabor.api.repository.listings.ListingCategoryRepository {
        @Override public void save(tech.nabor.api.model.listings.ListingCategory c) {}
        @Override public java.util.Optional<tech.nabor.api.model.listings.ListingCategory> findById(int id) { return java.util.Optional.empty(); }
        @Override public List<tech.nabor.api.model.listings.ListingCategory> findAll() { return List.of(); }
        @Override public List<tech.nabor.api.model.listings.ListingCategory> findByParent(int parentId) { return List.of(); }
    }

    static class StubEventCategoryRepository implements tech.nabor.api.repository.events.EvenementCategoryRepository {
        @Override public void save(tech.nabor.api.model.events.EvenementCategory c) {}
        @Override public java.util.Optional<tech.nabor.api.model.events.EvenementCategory> findById(int id) { return java.util.Optional.empty(); }
        @Override public List<tech.nabor.api.model.events.EvenementCategory> findAll() { return List.of(); }
        @Override public List<tech.nabor.api.model.events.EvenementCategory> findByParent(int parentId) { return List.of(); }
    }
}
