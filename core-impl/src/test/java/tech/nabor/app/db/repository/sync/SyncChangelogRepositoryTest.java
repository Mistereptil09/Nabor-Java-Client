// core-impl/src/test/java/tech/nabor/app/db/repository/sync/SyncChangelogRepositoryTest.java
package tech.nabor.app.db.repository.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.event.ChangeEvent;
import tech.nabor.api.model.sync.SyncChange;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncChangelogRepositoryTest extends BaseRepositoryTest {

    private AppSyncChangelogRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppSyncChangelogRepository(jdbi);
    }

    private ChangeEvent insertEvent(String table, String rowId) {
        return new ChangeEvent(table, rowId, "INSERT", null,
                Map.of("id", rowId, "title", "Test"),
                Instant.now()
        );
    }

    private ChangeEvent updateEvent(String table, String rowId) {
        return new ChangeEvent(table, rowId, "UPDATE",
                Map.of("severity", "low"),
                Map.of("severity", "high"),
                Instant.now()
        );
    }

    private ChangeEvent deleteEvent(String table, String rowId) {
        return new ChangeEvent(table, rowId, "DELETE",
                Map.of("id", rowId), null,
                Instant.now()
        );
    }

    // ── track ─────────────────────────────────────────────────────────────────

    @Test
    void track_insert_event() {
        repo.track(insertEvent("incidents", "row-1"));

        List<SyncChange> unsynced = repo.findUnsynced();
        assertEquals(1, unsynced.size());
        assertEquals("incidents", unsynced.getFirst().tableName());
        assertEquals("row-1",     unsynced.getFirst().rowId());
        assertEquals("INSERT",    unsynced.getFirst().operation());
        assertNull(unsynced.getFirst().previousValues());
        assertNotNull(unsynced.getFirst().newValues());
    }

    @Test
    void track_update_event_computes_changed_fields() {
        repo.track(updateEvent("incidents", "row-1"));

        SyncChange change = repo.findUnsynced().getFirst();
        assertEquals("UPDATE", change.operation());
        assertTrue(change.changedFields().contains("severity"));
    }

    @Test
    void track_delete_event() {
        repo.track(deleteEvent("incidents", "row-1"));

        SyncChange change = repo.findUnsynced().getFirst();
        assertEquals("DELETE", change.operation());
        assertNull(change.newValues());
        assertNotNull(change.previousValues());
    }

    // ── findUnsynced ──────────────────────────────────────────────────────────

    @Test
    void findUnsynced_returns_empty_when_no_changes() {
        assertTrue(repo.findUnsynced().isEmpty());
    }

    @Test
    void findUnsynced_returns_only_unsynced() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("incidents", "row-2"));

        repo.markSynced(repo.findUnsynced().getFirst().id());

        assertEquals(1, repo.findUnsynced().size());
        assertEquals("row-2", repo.findUnsynced().getFirst().rowId());
    }

    @Test
    void findUnsynced_ordered_by_changed_at_asc() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("incidents", "row-2"));

        List<SyncChange> unsynced = repo.findUnsynced();
        assertTrue(unsynced.getFirst().changedAt()
                .isBefore(unsynced.get(1).changedAt())
                || unsynced.getFirst().changedAt().equals(unsynced.get(1).changedAt())
        );
    }

    // ── findByTable ───────────────────────────────────────────────────────────

    @Test
    void findByTable_returns_only_matching_table() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("listings",  "row-2"));

        List<SyncChange> result = repo.findByTable("incidents");
        assertEquals(1, result.size());
        assertEquals("incidents", result.getFirst().tableName());
    }

    @Test
    void findByTable_returns_empty_when_no_match() {
        assertTrue(repo.findByTable("incidents").isEmpty());
    }

    // ── markSynced ────────────────────────────────────────────────────────────

    @Test
    void markSynced_sets_synced_at() {
        repo.track(insertEvent("incidents", "row-1"));
        int id = repo.findUnsynced().getFirst().id();

        repo.markSynced(id);

        assertTrue(repo.findUnsynced().isEmpty());
    }

    // ── markAllSynced ─────────────────────────────────────────────────────────

    @Test
    void markAllSynced_marks_all_unsynced() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("incidents", "row-2"));
        repo.track(insertEvent("incidents", "row-3"));

        repo.markAllSynced();

        assertTrue(repo.findUnsynced().isEmpty());
    }

    // ── deleteUnsynced ────────────────────────────────────────────────────────

    @Test
    void deleteUnsynced_removes_only_unsynced() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("incidents", "row-2"));
        repo.markSynced(repo.findUnsynced().getFirst().id());

        repo.deleteUnsynced();

        // row-1 était synced — reste dans findByTable
        // row-2 était unsynced — supprimé
        assertEquals(1, repo.findByTable("incidents").size());
    }

    @Test
    void deleteUnsynced_does_not_throw_when_empty() {
        assertDoesNotThrow(() -> repo.deleteUnsynced());
    }
}