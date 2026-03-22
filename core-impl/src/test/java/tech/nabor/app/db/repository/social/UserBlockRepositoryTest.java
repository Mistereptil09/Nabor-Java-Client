package tech.nabor.app.db.repository.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.social.UserBlock;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserBlockRepositoryTest extends BaseRepositoryTest {

    private AppUserBlockRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppUserBlockRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.basicUser("user-3", "c@test.com"));
    }

    private UserBlock block(String blockerId, String blockedId) {
        return new UserBlock(blockerId, blockedId, Instant.now());
    }

    // ── findByBlockerId ───────────────────────────────────────────────────────

    @Test
    void findByBlockerId_returns_blocked_users() {
        repo.save(block("user-1", "user-2"));
        repo.save(block("user-1", "user-3"));

        List<UserBlock> result = repo.findByBlockerId("user-1");
        assertEquals(2, result.size());
    }

    @Test
    void findByBlockerId_returns_empty_when_no_blocks() {
        assertTrue(repo.findByBlockerId("user-1").isEmpty());
    }

    @Test
    void findByBlockerId_does_not_return_other_blockers() {
        repo.save(block("user-2", "user-1"));
        assertTrue(repo.findByBlockerId("user-1").isEmpty());
    }

    // ── isBlocked ─────────────────────────────────────────────────────────────

    @Test
    void isBlocked_returns_true_when_blocked() {
        repo.save(block("user-1", "user-2"));
        assertTrue(repo.isBlocked("user-1", "user-2"));
    }

    @Test
    void isBlocked_returns_false_when_not_blocked() {
        assertFalse(repo.isBlocked("user-1", "user-2"));
    }

    @Test
    void isBlocked_is_not_symmetric() {
        repo.save(block("user-1", "user-2"));
        assertFalse(repo.isBlocked("user-2", "user-1"));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_block() {
        repo.save(block("user-1", "user-2"));
        assertTrue(repo.isBlocked("user-1", "user-2"));
    }

    @Test
    void save_updates_existing_block() {
        repo.save(block("user-1", "user-2"));
        Instant later = Instant.now().plusSeconds(10);
        repo.save(new UserBlock("user-1", "user-2", later));

        List<UserBlock> blocks = repo.findByBlockerId("user-1");
        assertEquals(1, blocks.size()); // pas de doublon
        assertEquals(later.toEpochMilli(), blocks.get(0).blockedAt().toEpochMilli());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_block() {
        repo.save(block("user-1", "user-2"));
        repo.delete("user-1", "user-2");
        assertFalse(repo.isBlocked("user-1", "user-2"));
    }

    @Test
    void delete_only_removes_target_block() {
        repo.save(block("user-1", "user-2"));
        repo.save(block("user-1", "user-3"));

        repo.delete("user-1", "user-2");

        assertFalse(repo.isBlocked("user-1", "user-2"));
        assertTrue(repo.isBlocked("user-1", "user-3"));
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("user-1", "user-2"));
    }
}