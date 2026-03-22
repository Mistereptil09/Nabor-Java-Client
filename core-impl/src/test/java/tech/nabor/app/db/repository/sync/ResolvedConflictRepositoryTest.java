// core-impl/src/test/java/tech/nabor/app/db/repository/sync/ResolvedConflictRepositoryTest.java
package tech.nabor.app.db.repository.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.sync.ResolvedConflict;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedConflictRepositoryTest extends BaseRepositoryTest {

    private AppResolvedConflictRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppResolvedConflictRepository(jdbi);
    }

    private ResolvedConflict resolved(String table, String rowId, String field, String chosen) {
        return new ResolvedConflict(0, table, rowId, field, chosen, Instant.now());
    }

    // ── findPrevious ──────────────────────────────────────────────────────────

    @Test
    void findPrevious_returns_empty_when_no_history() {
        assertTrue(repo.findPrevious("incidents", "row-1", "severity").isEmpty());
    }

    @Test
    void findPrevious_returns_most_recent_resolution() throws InterruptedException {
        repo.save(new ResolvedConflict(0, "incidents", "row-1", "severity",
                "local", Instant.now().minusSeconds(10)));
        repo.save(new ResolvedConflict(0, "incidents", "row-1", "severity",
                "remote", Instant.now()));

        ResolvedConflict previous = repo.findPrevious("incidents", "row-1", "severity").orElseThrow();
        assertEquals("remote", previous.chosenValue());
    }

    @Test
    void findPrevious_does_not_return_other_fields() {
        repo.save(resolved("incidents", "row-1", "status", "local"));

        assertTrue(repo.findPrevious("incidents", "row-1", "severity").isEmpty());
    }

    @Test
    void findPrevious_does_not_return_other_rows() {
        repo.save(resolved("incidents", "row-2", "severity", "local"));

        assertTrue(repo.findPrevious("incidents", "row-1", "severity").isEmpty());
    }

    // ── findByRow ─────────────────────────────────────────────────────────────

    @Test
    void findByRow_returns_all_resolutions_for_row() {
        repo.save(resolved("incidents", "row-1", "severity", "local"));
        repo.save(resolved("incidents", "row-1", "status",   "remote"));
        repo.save(resolved("incidents", "row-2", "severity", "local"));

        List<ResolvedConflict> result = repo.findByRow("incidents", "row-1");
        assertEquals(2, result.size());
    }

    @Test
    void findByRow_returns_empty_when_no_match() {
        assertTrue(repo.findByRow("incidents", "inexistant").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_persists_all_fields() {
        Instant now = Instant.now();
        repo.save(new ResolvedConflict(0, "incidents", "row-1", "severity", "local", now));

        ResolvedConflict saved = repo.findByRow("incidents", "row-1").get(0);
        assertEquals("incidents", saved.tableName());
        assertEquals("row-1",     saved.rowId());
        assertEquals("severity",  saved.fieldName());
        assertEquals("local",     saved.chosenValue());
        assertEquals(now.toEpochMilli(), saved.resolvedAt().toEpochMilli());
    }

    @Test
    void save_allows_multiple_resolutions_for_same_field() {
        repo.save(resolved("incidents", "row-1", "severity", "local"));
        repo.save(resolved("incidents", "row-1", "severity", "remote"));

        assertEquals(2, repo.findByRow("incidents", "row-1").size());
    }

    // ── deleteByRow ───────────────────────────────────────────────────────────

    @Test
    void deleteByRow_removes_all_resolutions_for_row() {
        repo.save(resolved("incidents", "row-1", "severity", "local"));
        repo.save(resolved("incidents", "row-1", "status",   "remote"));
        repo.save(resolved("incidents", "row-2", "severity", "local"));

        repo.deleteByRow("incidents", "row-1");

        assertTrue(repo.findByRow("incidents", "row-1").isEmpty());
        assertEquals(1, repo.findByRow("incidents", "row-2").size());
    }

    @Test
    void deleteByRow_does_not_throw_when_no_match() {
        assertDoesNotThrow(() -> repo.deleteByRow("incidents", "inexistant"));
    }
}