package tech.nabor.app.db.repository.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.sync.SyncState;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SyncStateRepositoryTest extends BaseRepositoryTest {

    private AppSyncStateRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppSyncStateRepository(jdbi);
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_returns_empty_when_no_state() {
        assertTrue(repo.get().isEmpty());
    }

    @Test
    void get_returns_state_when_exists() {
        Instant now = Instant.now();
        repo.save(new SyncState(now, "token-abc", false));

        SyncState state = repo.get().orElseThrow();
        assertEquals(now.toEpochMilli(), state.lastSyncedAt().toEpochMilli());
        assertEquals("token-abc", state.lastSyncToken());
        assertFalse(state.isRollingBack());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_state() {
        repo.save(new SyncState(null, null, false));
        assertTrue(repo.get().isPresent());
    }

    @Test
    void save_updates_existing_state() {
        repo.save(new SyncState(null, "token-1", false));
        repo.save(new SyncState(null, "token-2", true));

        SyncState state = repo.get().orElseThrow();
        assertEquals("token-2", state.lastSyncToken());
        assertTrue(state.isRollingBack());
    }

    @Test
    void save_only_one_row_exists() {
        repo.save(new SyncState(null, "token-1", false));
        repo.save(new SyncState(null, "token-2", false));
        repo.save(new SyncState(null, "token-3", false));

        // vérifie qu'il n'y a toujours qu'une seule ligne
        assertEquals("token-3", repo.get().orElseThrow().lastSyncToken());
    }

    @Test
    void save_persists_null_last_synced_at() {
        repo.save(new SyncState(null, null, false));
        assertNull(repo.get().orElseThrow().lastSyncedAt());
    }

    // ── updateLastSyncedAt ────────────────────────────────────────────────────

    @Test
    void updateLastSyncedAt_updates_timestamp() {
        repo.save(new SyncState(null, null, false));
        Instant now = Instant.now();

        repo.updateLastSyncedAt(now);

        assertEquals(now.toEpochMilli(), repo.get().orElseThrow().lastSyncedAt().toEpochMilli());
    }

    @Test
    void updateLastSyncedAt_does_not_affect_other_fields() {
        repo.save(new SyncState(null, "token-abc", false));
        repo.updateLastSyncedAt(Instant.now());

        assertEquals("token-abc", repo.get().orElseThrow().lastSyncToken());
    }

    // ── updateSyncToken ───────────────────────────────────────────────────────

    @Test
    void updateSyncToken_updates_token() {
        repo.save(new SyncState(null, "old-token", false));
        repo.updateSyncToken("new-token");

        assertEquals("new-token", repo.get().orElseThrow().lastSyncToken());
    }

    @Test
    void updateSyncToken_does_not_affect_other_fields() {
        Instant now = Instant.now();
        repo.save(new SyncState(now, "old-token", false));
        repo.updateSyncToken("new-token");

        assertEquals(now.toEpochMilli(), repo.get().orElseThrow().lastSyncedAt().toEpochMilli());
    }

    // ── setRollingBack ────────────────────────────────────────────────────────

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