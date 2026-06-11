package tech.nabor.app.db.repository.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.sync.SyncState;
import tech.nabor.app.db.BaseRepositoryTest;

import static org.junit.jupiter.api.Assertions.*;

class SyncStateRepositoryTest extends BaseRepositoryTest {

    private AppSyncStateRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppSyncStateRepository(jdbi);
    }

    @Test
    void get_returns_empty_when_no_state() {
        assertTrue(repo.get().isEmpty());
    }

    @Test
    void save_and_get() {
        repo.save(new SyncState("cursor-abc", "resume-xyz", false));
        SyncState state = repo.get().orElseThrow();
        assertEquals("cursor-abc", state.latestSyncCursor());
        assertEquals("resume-xyz", state.resumeCursor());
        assertFalse(state.isRollingBack());
    }

    @Test
    void save_updates_existing_state() {
        repo.save(new SyncState(null, null, false));
        repo.save(new SyncState("cursor-1", "resume-1", true));
        SyncState state = repo.get().orElseThrow();
        assertEquals("cursor-1", state.latestSyncCursor());
        assertEquals("resume-1", state.resumeCursor());
        assertTrue(state.isRollingBack());
    }

    @Test
    void save_only_one_row_exists() {
        repo.save(new SyncState(null, null, false));
        repo.save(new SyncState("c2", null, false));
        repo.save(new SyncState("c3", null, false));
        assertEquals("c3", repo.get().orElseThrow().latestSyncCursor());
    }

    @Test
    void save_persists_null_cursors() {
        repo.save(new SyncState(null, null, false));
        assertNull(repo.get().orElseThrow().latestSyncCursor());
        assertNull(repo.get().orElseThrow().resumeCursor());
    }

    @Test
    void updateLatestCursor_sets_and_clears_resume() {
        repo.save(new SyncState(null, "old-resume", false));
        repo.updateLatestCursor("final-cursor");
        SyncState state = repo.get().orElseThrow();
        assertEquals("final-cursor", state.latestSyncCursor());
        assertNull(state.resumeCursor());
    }

    @Test
    void updateLatestCursor_does_not_affect_rolling_back() {
        repo.save(new SyncState(null, null, true));
        repo.updateLatestCursor("final-cursor");
        assertTrue(repo.get().orElseThrow().isRollingBack());
    }

    @Test
    void updateResumeCursor_sets_cursor() {
        repo.save(new SyncState(null, null, false));
        repo.updateResumeCursor("page-2-cursor");
        assertEquals("page-2-cursor", repo.get().orElseThrow().resumeCursor());
    }

    @Test
    void updateResumeCursor_does_not_affect_other_fields() {
        repo.save(new SyncState("latest", null, false));
        repo.updateResumeCursor("page-2-cursor");
        assertEquals("latest", repo.get().orElseThrow().latestSyncCursor());
    }

    @Test
    void setRollingBack_sets_true() {
        repo.save(new SyncState(null, null, false));
        repo.setRollingBack(true);
        assertTrue(repo.get().orElseThrow().isRollingBack());
    }

    @Test
    void setRollingBack_sets_false() {
        repo.save(new SyncState(null, null, true));
        repo.setRollingBack(false);
        assertFalse(repo.get().orElseThrow().isRollingBack());
    }
}
