// core-impl/src/test/java/tech/nabor/app/db/repository/user/UserSessionRepositoryTest.java
package tech.nabor.app.db.repository.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.user.UserSession;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserSessionRepositoryTest extends BaseRepositoryTest {

    private AppUserSessionRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppUserSessionRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
    }

    private UserSession session(String id, String userId, String tokenHash) {
        return new UserSession(
                id, userId, tokenHash, "Chrome", "127.0.0.1",
                "Mozilla/5.0", Instant.now(),
                Instant.now().plusSeconds(3600), null
        );
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_session_when_exists() {
        repo.save(session("session-1", "user-1", "hash-1"));

        Optional<UserSession> found = repo.findById("session-1");
        assertTrue(found.isPresent());
        assertEquals("user-1", found.get().userId());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    // ── findByTokenHash ───────────────────────────────────────────────────────

    @Test
    void findByTokenHash_returns_session_when_exists() {
        repo.save(session("session-1", "user-1", "hash-abc"));

        Optional<UserSession> found = repo.findByTokenHash("hash-abc");
        assertTrue(found.isPresent());
        assertEquals("session-1", found.get().id());
    }

    @Test
    void findByTokenHash_returns_empty_when_not_found() {
        assertTrue(repo.findByTokenHash("inexistant").isEmpty());
    }

    // ── findActiveByUserId ────────────────────────────────────────────────────

    @Test
    void findActiveByUserId_returns_only_active_sessions() {
        repo.save(session("session-1", "user-1", "hash-1"));
        repo.save(session("session-2", "user-1", "hash-2"));
        repo.revoke("session-1");

        List<UserSession> active = repo.findActiveByUserId("user-1");
        assertEquals(1, active.size());
        assertEquals("session-2", active.get(0).id());
    }

    @Test
    void findActiveByUserId_does_not_return_other_users_sessions() {
        repo.save(session("session-1", "user-2", "hash-1"));

        assertTrue(repo.findActiveByUserId("user-1").isEmpty());
    }

    @Test
    void findActiveByUserId_returns_empty_when_all_revoked() {
        repo.save(session("session-1", "user-1", "hash-1"));
        repo.revoke("session-1");

        assertTrue(repo.findActiveByUserId("user-1").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_session() {
        repo.save(session("session-1", "user-1", "hash-1"));
        assertTrue(repo.findById("session-1").isPresent());
    }

    @Test
    void save_updates_existing_session() {
        repo.save(session("session-1", "user-1", "hash-1"));

        UserSession updated = new UserSession(
                "session-1", "user-1", "hash-new", "Firefox",
                "192.168.1.1", "Firefox/120", Instant.now(),
                Instant.now().plusSeconds(7200), null
        );
        repo.save(updated);

        UserSession found = repo.findById("session-1").orElseThrow();
        assertEquals("hash-new", found.refreshTokenHash());
        assertEquals("Firefox",  found.deviceName());
    }

    // ── revoke ────────────────────────────────────────────────────────────────

    @Test
    void revoke_sets_revoked_at() {
        repo.save(session("session-1", "user-1", "hash-1"));
        repo.revoke("session-1");

        UserSession found = repo.findById("session-1").orElseThrow();
        assertNotNull(found.revokedAt());
    }

    @Test
    void revoke_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.revoke("inexistant"));
    }

    // ── revokeAllForUser ──────────────────────────────────────────────────────

    @Test
    void revokeAllForUser_revokes_all_active_sessions() {
        repo.save(session("session-1", "user-1", "hash-1"));
        repo.save(session("session-2", "user-1", "hash-2"));
        repo.save(session("session-3", "user-1", "hash-3"));

        repo.revokeAllForUser("user-1");

        assertTrue(repo.findActiveByUserId("user-1").isEmpty());
    }

    @Test
    void revokeAllForUser_does_not_affect_other_users() {
        repo.save(session("session-1", "user-1", "hash-1"));
        repo.save(session("session-2", "user-2", "hash-2"));

        repo.revokeAllForUser("user-1");

        assertEquals(1, repo.findActiveByUserId("user-2").size());
    }

    @Test
    void revokeAllForUser_does_not_throw_when_no_sessions() {
        assertDoesNotThrow(() -> repo.revokeAllForUser("user-1"));
    }
}