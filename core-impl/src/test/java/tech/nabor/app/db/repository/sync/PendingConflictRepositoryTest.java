// core-impl/src/test/java/tech/nabor/app/db/repository/sync/PendingConflictRepositoryTest.java
package tech.nabor.app.db.repository.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PendingConflictRepositoryTest extends BaseRepositoryTest {

    private AppPendingConflictRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppPendingConflictRepository(jdbi);
    }

    private PendingConflict conflict(String table, String rowId, String field) {
        return new PendingConflict(0, table, rowId, field, "local-val", "remote-val", Instant.now());
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returns_empty_when_no_conflicts() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAll_returns_all_conflicts() {
        repo.save(conflict("incidents", "row-1", "severity"));
        repo.save(conflict("incidents", "row-1", "status"));
        repo.save(conflict("listings",  "row-2", "title"));

        assertEquals(3, repo.findAll().size());
    }

    // ── findByTable ───────────────────────────────────────────────────────────

    @Test
    void findByTable_returns_only_matching_table() {
        repo.save(conflict("incidents", "row-1", "severity"));
        repo.save(conflict("listings",  "row-2", "title"));

        List<PendingConflict> result = repo.findByTable("incidents");
        assertEquals(1, result.size());
        assertEquals("incidents", result.get(0).tableName());
    }

    // ── findByRow ─────────────────────────────────────────────────────────────

    @Test
    void findByRow_returns_all_fields_for_row() {
        repo.save(conflict("incidents", "row-1", "severity"));
        repo.save(conflict("incidents", "row-1", "status"));
        repo.save(conflict("incidents", "row-2", "severity"));

        List<PendingConflict> result = repo.findByRow("incidents", "row-1");
        assertEquals(2, result.size());
    }

    @Test
    void findByRow_returns_empty_when_no_match() {
        assertTrue(repo.findByRow("incidents", "inexistant").isEmpty());
    }

    // ── hasConflicts ──────────────────────────────────────────────────────────

    @Test
    void hasConflicts_returns_false_when_empty() {
        assertFalse(repo.hasConflicts());
    }

    @Test
    void hasConflicts_returns_true_when_conflicts_exist() {
        repo.save(conflict("incidents", "row-1", "severity"));
        assertTrue(repo.hasConflicts());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_persists_all_fields() {
        Instant now = Instant.now();
        repo.save(new PendingConflict(0, "incidents", "row-1", "severity",
                "high", "low", now));

        PendingConflict saved = repo.findAll().get(0);
        assertEquals("incidents", saved.tableName());
        assertEquals("row-1",     saved.rowId());
        assertEquals("severity",  saved.fieldName());
        assertEquals("high",      saved.localValue());
        assertEquals("low",       saved.remoteValue());
        assertEquals(now.toEpochMilli(), saved.detectedAt().toEpochMilli());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_conflict_by_id() {
        repo.save(conflict("incidents", "row-1", "severity"));
        int id = repo.findAll().get(0).id();

        repo.delete(id);

        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void delete_only_removes_target_conflict() {
        repo.save(conflict("incidents", "row-1", "severity"));
        repo.save(conflict("incidents", "row-1", "status"));
        int id = repo.findAll().get(0).id();

        repo.delete(id);

        assertEquals(1, repo.findAll().size());
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete(999));
    }

    // ── deleteAll ─────────────────────────────────────────────────────────────

    @Test
    void deleteAll_removes_all_conflicts() {
        repo.save(conflict("incidents", "row-1", "severity"));
        repo.save(conflict("incidents", "row-2", "status"));

        repo.deleteAll();

        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void deleteAll_does_not_throw_when_empty() {
        assertDoesNotThrow(() -> repo.deleteAll());
    }
}