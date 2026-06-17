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
                null, Instant.now());
    }

    private ChangeEvent updateEvent(String table, String rowId) {
        return new ChangeEvent(table, rowId, "UPDATE",
                Map.of("severity", "low"),
                Map.of("severity", "high"),
                "2025-06-01T00:00:00Z", Instant.now());
    }

    private ChangeEvent deleteEvent(String table, String rowId) {
        return new ChangeEvent(table, rowId, "DELETE",
                Map.of("id", rowId), null,
                "2025-06-01T00:00:00Z", Instant.now());
    }

    // ── track ─────────────────────────────────────────────────────────────────

    @Test
    void track_insert_event() {
        repo.track(insertEvent("incidents", "row-1"));
        List<SyncChange> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals("incidents", all.getFirst().tableName());
        assertEquals("INSERT", all.getFirst().operation());
    }

    @Test
    void track_update_event() {
        repo.track(updateEvent("incidents", "row-1"));
        SyncChange change = repo.findAll().getFirst();
        assertEquals("UPDATE", change.operation());
        assertEquals("2025-06-01T00:00:00Z", change.baseUpdatedAt());
    }

    @Test
    void track_delete_event() {
        repo.track(deleteEvent("incidents", "row-1"));
        SyncChange change = repo.findAll().getFirst();
        assertEquals("DELETE", change.operation());
    }

    // ── findAll ──────────────────────────────────────────────────────────────

    @Test
    void findAll_returns_empty_when_no_changes() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAll_returns_all_entries() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("incidents", "row-2"));
        assertEquals(2, repo.findAll().size());
    }

    // ── findByTable ───────────────────────────────────────────────────────────

    @Test
    void findByTable_returns_only_matching_table() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("listings",  "row-2"));
        assertEquals(1, repo.findByTable("incidents").size());
    }

    // ── deleteByTableAndRow ──────────────────────────────────────────────────

    @Test
    void deleteByTableAndRow_removes_specific_entry() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("incidents", "row-2"));
        repo.deleteByTableAndRow("incidents", "row-1");
        assertEquals(1, repo.findAll().size());
    }

    // ── deleteAll ────────────────────────────────────────────────────────────

    @Test
    void deleteAll_removes_everything() {
        repo.track(insertEvent("incidents", "row-1"));
        repo.track(insertEvent("incidents", "row-2"));
        repo.deleteAll();
        assertTrue(repo.findAll().isEmpty());
    }

    // ── deleteById ───────────────────────────────────────────────────────────

    @Test
    void deleteById_removes_single_entry() {
        repo.track(insertEvent("incidents", "row-1"));
        int id = repo.findAll().getFirst().id();
        repo.deleteById(id);
        assertTrue(repo.findAll().isEmpty());
    }
}
