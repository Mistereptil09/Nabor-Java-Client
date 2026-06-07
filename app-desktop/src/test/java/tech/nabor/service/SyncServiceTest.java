package tech.nabor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.nabor.api.EventBus;
import tech.nabor.api.NaborHttpClient;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.PendingConflict;
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

class SyncServiceTest {

    private StubSqliteRepository db;
    private StubPendingConflictRepository pendingConflicts;
    private StubResolvedConflictRepository resolvedConflicts;
    private StubIncidentRepository incidents;
    private StubEventBus eventBus;
    private SyncService service;

    @BeforeEach
    void setUp() {
        db = new StubSqliteRepository();
        pendingConflicts = new StubPendingConflictRepository();
        resolvedConflicts = new StubResolvedConflictRepository();
        incidents = new StubIncidentRepository();
        eventBus = new StubEventBus();

        db.setPendingConflicts(pendingConflicts);
        db.setResolvedConflicts(resolvedConflicts);
        db.setIncidents(incidents);

        PluginContext ctx = new StubPluginContext(db, eventBus);
        service = new SyncService(ctx);
    }

    // ── detectConflicts ──────────────────────────────────────────────────────

    @Test
    void detectConflicts_bothModified_returnsConflict() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        Incident local = incident("inc-1", "Title locale", IncidentStatus.open,
                Instant.parse("2025-06-02T00:00:00Z"));
        Incident remote = incident("inc-1", "Title distant", IncidentStatus.open,
                Instant.parse("2025-06-03T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(local, remote, lastSync);

        assertEquals(1, conflicts.size());
        assertEquals("incidents", conflicts.get(0).tableName());
        assertEquals("inc-1", conflicts.get(0).rowId());
        assertEquals("title", conflicts.get(0).fieldName());
        assertEquals("Title locale", conflicts.get(0).localValue());
        assertEquals("Title distant", conflicts.get(0).remoteValue());
    }

    @Test
    void detectConflicts_remoteChangedOnly_noConflict() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        // local updated BEFORE lastSync → not modified since sync
        Incident local = incident("inc-1", "Titre", IncidentStatus.open,
                Instant.parse("2025-05-30T00:00:00Z"));
        // remote updated AFTER lastSync
        Incident remote = incident("inc-1", "Titre modifié", IncidentStatus.open,
                Instant.parse("2025-06-03T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(local, remote, lastSync);

        // Remote change should be auto-applied, not flagged as conflict
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_neitherModified_noConflict() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        Incident local = incident("inc-1", "Titre", IncidentStatus.open,
                Instant.parse("2025-05-30T00:00:00Z"));
        Incident remote = incident("inc-1", "Titre", IncidentStatus.open,
                Instant.parse("2025-05-31T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(local, remote, lastSync);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_sameValues_noConflict() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        Incident local = incident("inc-1", "Même titre", IncidentStatus.open,
                Instant.parse("2025-06-02T00:00:00Z"));
        Incident remote = incident("inc-1", "Même titre", IncidentStatus.open,
                Instant.parse("2025-06-03T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(local, remote, lastSync);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_multipleFields_returnsAll() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        Incident local = incident("inc-2", "Titre A", IncidentSeverity.low, IncidentStatus.open,
                Instant.parse("2025-06-02T00:00:00Z"));
        Incident remote = incident("inc-2", "Titre B", IncidentSeverity.critical, IncidentStatus.in_progress,
                Instant.parse("2025-06-03T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(local, remote, lastSync);

        assertEquals(3, conflicts.size()); // title, severity, status all differ
    }

    @Test
    void detectConflicts_nullUpdatedAt_noConflict() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        Incident local = incident("inc-1", "Titre A", IncidentStatus.open, null);
        Incident remote = incident("inc-1", "Titre B", IncidentStatus.open, null);

        List<PendingConflict> conflicts = service.detectConflicts(local, remote, lastSync);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_nullRemote_returnsEmpty() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        Incident local = incident("inc-1", "Titre", IncidentStatus.open,
                Instant.parse("2025-06-02T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(local, null, lastSync);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_nullLocal_returnsEmpty() {
        Instant lastSync = Instant.parse("2025-06-01T00:00:00Z");
        Incident remote = incident("inc-1", "Titre", IncidentStatus.open,
                Instant.parse("2025-06-02T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(null, remote, lastSync);

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void detectConflicts_nullLastSync_allModified_returnsConflict() {
        // When lastSync is null, isAfter(null) returns true (reference == null),
        // so both are considered changed → conflict for any difference.
        Incident local = incident("inc-1", "A", IncidentStatus.open,
                Instant.parse("2025-06-02T00:00:00Z"));
        Incident remote = incident("inc-1", "B", IncidentStatus.open,
                Instant.parse("2025-06-02T00:00:00Z"));

        List<PendingConflict> conflicts = service.detectConflicts(local, remote, null);

        assertEquals(1, conflicts.size());
    }

    // ── resolve ──────────────────────────────────────────────────────────────

    @Test
    void resolve_localChoice_persistsResolution() {
        PendingConflict conflict = new PendingConflict(
                1, "incidents", "inc-1", "title",
                "local value", "remote value", Instant.now());
        pendingConflicts.save(conflict);

        service.resolve(conflict, SyncService.Choice.LOCAL);

        // Conflict removed
        assertTrue(pendingConflicts.findAll().isEmpty());
        // Resolution recorded
        var resolutions = resolvedConflicts.findByRow("incidents", "inc-1");
        assertFalse(resolutions.isEmpty());
        assertEquals("local", resolutions.get(0).chosenValue());
    }

    @Test
    void resolve_remoteChoice_appliesRemoteValue() {
        // Seed an incident in the stub DB
        Incident existing = incident("inc-1", "Ancien titre", IncidentSeverity.medium,
                IncidentStatus.open, Instant.parse("2025-06-01T00:00:00Z"));
        incidents.save(existing);

        PendingConflict conflict = new PendingConflict(
                1, "incidents", "inc-1", "title",
                "Ancien titre", "Nouveau titre", Instant.now());
        pendingConflicts.save(conflict);

        service.resolve(conflict, SyncService.Choice.REMOTE);

        assertTrue(pendingConflicts.findAll().isEmpty());

        // Verify the incident title was updated
        Optional<Incident> updated = incidents.findById("inc-1");
        assertTrue(updated.isPresent());
        assertEquals("Nouveau titre", updated.get().title());
    }

    @Test
    void resolve_remoteChoice_updatesSeverityEnum() {
        Incident existing = incident("inc-1", "Titre", IncidentSeverity.low,
                IncidentStatus.open, Instant.parse("2025-06-01T00:00:00Z"));
        incidents.save(existing);

        PendingConflict conflict = new PendingConflict(
                1, "incidents", "inc-1", "severity",
                "low", "critical", Instant.now());
        pendingConflicts.save(conflict);

        service.resolve(conflict, SyncService.Choice.REMOTE);

        Optional<Incident> updated = incidents.findById("inc-1");
        assertTrue(updated.isPresent());
        assertEquals(IncidentSeverity.critical, updated.get().severity());
    }

    @Test
    void resolve_remoteChoice_updatesStatusEnum() {
        Incident existing = incident("inc-1", "Titre", IncidentSeverity.medium,
                IncidentStatus.open, Instant.parse("2025-06-01T00:00:00Z"));
        incidents.save(existing);

        PendingConflict conflict = new PendingConflict(
                1, "incidents", "inc-1", "status",
                "open", "resolved", Instant.now());
        pendingConflicts.save(conflict);

        service.resolve(conflict, SyncService.Choice.REMOTE);

        Optional<Incident> updated = incidents.findById("inc-1");
        assertTrue(updated.isPresent());
        assertEquals(IncidentStatus.resolved, updated.get().status());
    }

    // ── hasConflicts / pendingConflicts ──────────────────────────────────────

    @Test
    void hasConflicts_noConflicts_returnsFalse() {
        assertFalse(service.hasConflicts());
    }

    @Test
    void hasConflicts_withConflicts_returnsTrue() {
        pendingConflicts.save(new PendingConflict(
                1, "incidents", "inc-1", "status", "open", "resolved", Instant.now()));

        assertTrue(service.hasConflicts());
        assertEquals(1, service.pendingConflicts().size());
    }

    // ── markSynced ──────────────────────────────────────────────────────────

    @Test
    void markSynced_updatesSyncState() {
        assertFalse(db.syncState().get().isPresent());

        service.markSynced();

        assertTrue(db.syncState().get().isPresent());
        assertNotNull(db.syncState().get().get().lastSyncedAt());
    }

    // ── lastSync ─────────────────────────────────────────────────────────────

    @Test
    void lastSync_noState_returnsEmpty() {
        assertFalse(service.lastSync().isPresent());
    }

    // ── unsyncedChangesCount ─────────────────────────────────────────────────

    @Test
    void unsyncedChangesCount_returnsZero_whenEmpty() {
        assertEquals(0, service.unsyncedChangesCount());
    }

    // ── simulateRemoteConflict ───────────────────────────────────────────────

    @Test
    void simulateRemoteConflict_noIncidents_returnsFalse() {
        assertFalse(service.simulateRemoteConflict());
    }

    @Test
    void simulateRemoteConflict_withIncident_createsConflict() {
        Incident local = incident("inc-1", "Titre", IncidentStatus.open,
                Instant.parse("2025-06-01T00:00:00Z"));
        incidents.save(local);

        assertTrue(service.simulateRemoteConflict());
        assertTrue(service.hasConflicts());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Incident incident(String id, String title, IncidentStatus status, Instant updatedAt) {
        return incident(id, title, IncidentSeverity.medium, status, updatedAt);
    }

    private static Incident incident(String id, String title, IncidentSeverity severity,
                                      IncidentStatus status, Instant updatedAt) {
        return new Incident(
                id, "reporter-1", null, null, null,
                title, "Description", severity, status,
                null, Instant.parse("2025-05-01T00:00:00Z"),
                updatedAt, null,
                null, null, false);
    }

    // ── Stubs ────────────────────────────────────────────────────────────────

    static class StubEventBus implements EventBus {
        final List<String> published = new ArrayList<>();
        @Override public void publish(String event, Object payload) {
            published.add(event);
        }
        @Override public void subscribe(String event, java.util.function.Consumer<Object> handler) {}
        @Override public void unsubscribe(String event, java.util.function.Consumer<Object> handler) {}
    }

    static class StubPluginContext implements PluginContext {
        private final SqliteRepository db;
        private final EventBus eventBus;
        StubPluginContext(SqliteRepository db, EventBus eventBus) {
            this.db = db;
            this.eventBus = eventBus;
        }
        @Override public NaborHttpClient getHttpClient() { return null; }
        @Override public SqliteRepository getDb() { return db; }
        @Override public tech.nabor.api.ConnectedUser getConnectedUser() { return null; }
        @Override public tech.nabor.api.I18n getI18n() { return null; }
        @Override public EventBus getEventBus() { return eventBus; }
        @Override public NaborReporter getReporter() { return null; }
    }

    static class StubSqliteRepository implements SqliteRepository {
        private StubPendingConflictRepository pendingConflicts = new StubPendingConflictRepository();
        private StubResolvedConflictRepository resolvedConflicts = new StubResolvedConflictRepository();
        private StubIncidentRepository incidents = new StubIncidentRepository();
        private StubSyncStateRepository syncState = new StubSyncStateRepository();
        private StubSyncChangelogRepository syncChangelog = new StubSyncChangelogRepository();

        void setPendingConflicts(StubPendingConflictRepository r) { this.pendingConflicts = r; }
        void setResolvedConflicts(StubResolvedConflictRepository r) { this.resolvedConflicts = r; }
        void setIncidents(StubIncidentRepository r) { this.incidents = r; }

        @Override public SyncStateRepository syncState() { return syncState; }
        @Override public SyncChangelogRepository syncChangelog() { return syncChangelog; }
        @Override public PendingConflictRepository pendingConflicts() { return pendingConflicts; }
        @Override public ResolvedConflictRepository resolvedConflicts() { return resolvedConflicts; }
        @Override public IncidentRepository incidents() { return incidents; }

        // Unused repository methods — return null (won't be called in tests)
        @Override public tech.nabor.api.repository.local.LocalAccountRepository localAccounts() { return null; }
        @Override public tech.nabor.api.repository.local.LocaleConfigRepository localeConfigs() { return null; }
        @Override public tech.nabor.api.repository.local.PluginStateRepository pluginStates() { return null; }
        @Override public tech.nabor.api.repository.local.PluginConfigRepository pluginConfigs() { return null; }
        @Override public tech.nabor.api.repository.user.UserRepository users() { return null; }
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

    static class StubPendingConflictRepository implements PendingConflictRepository {
        private final List<PendingConflict> items = new ArrayList<>();
        private int nextId = 1;

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
        @Override public void save(PendingConflict c) {
            items.add(new PendingConflict(nextId++, c.tableName(), c.rowId(),
                    c.fieldName(), c.localValue(), c.remoteValue(), c.detectedAt()));
        }
        @Override public void delete(int id) {
            items.removeIf(c -> c.id() == id);
        }
        @Override public void deleteAll() { items.clear(); }
    }

    static class StubResolvedConflictRepository implements ResolvedConflictRepository {
        private final List<tech.nabor.api.model.sync.ResolvedConflict> items = new ArrayList<>();

        @Override public Optional<tech.nabor.api.model.sync.ResolvedConflict> findPrevious(
                String tableName, String rowId, String fieldName) {
            return items.stream()
                    .filter(c -> c.tableName().equals(tableName)
                            && c.rowId().equals(rowId)
                            && c.fieldName().equals(fieldName))
                    .findFirst();
        }
        @Override public List<tech.nabor.api.model.sync.ResolvedConflict> findByRow(
                String tableName, String rowId) {
            return items.stream()
                    .filter(c -> c.tableName().equals(tableName) && c.rowId().equals(rowId))
                    .toList();
        }
        @Override public void save(tech.nabor.api.model.sync.ResolvedConflict c) {
            items.add(c);
        }
        @Override public void deleteByRow(String tableName, String rowId) {
            items.removeIf(c -> c.tableName().equals(tableName) && c.rowId().equals(rowId));
        }
    }

    static class StubIncidentRepository implements IncidentRepository {
        private final List<Incident> items = new ArrayList<>();

        @Override public Optional<Incident> findById(String id) {
            return items.stream().filter(i -> i.id().equals(id)).findFirst();
        }
        @Override public List<Incident> findByReporterId(String reporterId) {
            return items.stream().filter(i -> i.reporterId().equals(reporterId)).toList();
        }
        @Override public List<Incident> findByAssignedTo(String userId) {
            return items.stream().filter(i -> userId.equals(i.assignedTo())).toList();
        }
        @Override public List<Incident> findByNeighbourhood(String neighbourhoodId, int limit) {
            return items.stream()
                    .filter(i -> neighbourhoodId.equals(i.neighbourhoodId()))
                    .limit(limit).toList();
        }
        @Override public List<Incident> findByStatus(IncidentStatus status, int limit) {
            return items.stream()
                    .filter(i -> i.status() == status)
                    .limit(limit).toList();
        }
        @Override public List<Incident> findBySeverity(IncidentSeverity severity, int limit) {
            return items.stream()
                    .filter(i -> i.severity() == severity)
                    .limit(limit).toList();
        }
        @Override public List<Incident> findOpen(String neighbourhoodId, int limit) {
            return items.stream()
                    .filter(i -> i.status() == IncidentStatus.open
                            || i.status() == IncidentStatus.in_progress)
                    .filter(i -> neighbourhoodId.equals(i.neighbourhoodId()))
                    .limit(limit).toList();
        }
        @Override public List<Incident> findDirty() {
            return items.stream().filter(Incident::isDirty).toList();
        }
        @Override public void save(Incident incident) {
            items.removeIf(i -> i.id().equals(incident.id()));
            items.add(incident);
        }
        @Override public void assign(String id, String userId) {
            findById(id).ifPresent(i -> {
                Incident updated = new Incident(i.id(), i.reporterId(), userId,
                        i.neighbourhoodId(), i.mongoDocumentId(),
                        i.title(), i.description(), i.severity(), IncidentStatus.in_progress,
                        Instant.now(), i.createdAt(), Instant.now(), i.resolvedAt(),
                        i.baseUpdatedAt(), i.syncedAt(), true);
                save(updated);
            });
        }
        @Override public void resolve(String id) {
            findById(id).ifPresent(i -> {
                Incident updated = new Incident(i.id(), i.reporterId(), i.assignedTo(),
                        i.neighbourhoodId(), i.mongoDocumentId(),
                        i.title(), i.description(), i.severity(), IncidentStatus.resolved,
                        i.assignedAt(), i.createdAt(), Instant.now(), Instant.now(),
                        i.baseUpdatedAt(), i.syncedAt(), true);
                save(updated);
            });
        }
    }

    static class StubSyncStateRepository implements SyncStateRepository {
        private SyncState state;

        @Override public Optional<SyncState> get() { return Optional.ofNullable(state); }
        @Override public void save(SyncState s) { this.state = s; }
        @Override public void updateLastSyncedAt(Instant at) {
            if (state != null) state = new SyncState(at, state.lastSyncToken(), state.isRollingBack());
        }
        @Override public void updateSyncToken(String token) {
            if (state != null) state = new SyncState(state.lastSyncedAt(), token, state.isRollingBack());
        }
        @Override public void setRollingBack(boolean rollingBack) {
            if (state != null) state = new SyncState(state.lastSyncedAt(), state.lastSyncToken(), rollingBack);
        }
    }

    static class StubSyncChangelogRepository implements SyncChangelogRepository {
        @Override public List<tech.nabor.api.model.sync.SyncChange> findUnsynced() {
            return List.of();
        }
        @Override public List<tech.nabor.api.model.sync.SyncChange> findByTable(String tableName) {
            return List.of();
        }
        @Override public void track(tech.nabor.api.event.ChangeEvent event) {}
        @Override public void markSynced(int id) {}
        @Override public void markAllSynced() {}
        @Override public void deleteUnsynced() {}
    }
}
