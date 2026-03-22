// core-impl/src/test/java/tech/nabor/app/db/repository/user/UserRepositoryTest.java
package tech.nabor.app.db.repository.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.user.User;
import tech.nabor.api.model.enums.MessagePolicy;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.enums.Visibility;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import tech.nabor.app.db.fixtures.UserFixtures;

class UserRepositoryTest extends BaseRepositoryTest {

    private AppUserRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppUserRepository(jdbi);
    }

    private User user(String id, String email) {
        return UserFixtures.basicUser(id, email);
    }

    private User userWithRole(String id, String email, UserRole role) {
        return UserFixtures.userWithRole(id, email, role);
    }


    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_user_when_exists() {
        repo.save(user("user-1", "a@test.com"));

        Optional<User> found = repo.findById("user-1");
        assertTrue(found.isPresent());
        assertEquals("user-1",    found.get().id());
        assertEquals("Antonio",   found.get().firstName());
        assertEquals("a@test.com",found.get().email());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    @Test
    void findById_returns_soft_deleted_user() {
        repo.save(user("user-1", "a@test.com"));
        repo.delete("user-1");

        // findById retourne même les soft-deleted
        assertTrue(repo.findById("user-1").isPresent());
        assertNotNull(repo.findById("user-1").get().deletedAt());
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    void findByEmail_returns_user_when_exists() {
        repo.save(user("user-1", "a@test.com"));

        Optional<User> found = repo.findByEmail("a@test.com");
        assertTrue(found.isPresent());
        assertEquals("user-1", found.get().id());
    }

    @Test
    void findByEmail_returns_empty_when_not_found() {
        assertTrue(repo.findByEmail("inexistant@test.com").isEmpty());
    }

    @Test
    void findByEmail_does_not_return_soft_deleted() {
        repo.save(user("user-1", "a@test.com"));
        repo.delete("user-1");

        assertTrue(repo.findByEmail("a@test.com").isEmpty());
    }

    // ── findByNeighbourhood ───────────────────────────────────────────────────

    @Test
    void findByNeighbourhood_returns_users_in_neighbourhood() {
        repo.save(user("user-1", "a@test.com"));
        repo.save(user("user-2", "b@test.com"));
        repo.save(new User(
                "user-3", "C", "D", "c@test.com", "hash", null,
                null, "other-neighbourhood", Visibility.public_,
                null, MessagePolicy.open, "fr", null, null,
                UserRole.resident, null, null,
                Instant.now(), null, null
        ));

        List<User> result = repo.findByNeighbourhood("neighbourhood-1");
        assertEquals(2, result.size());
    }

    @Test
    void findByNeighbourhood_does_not_return_soft_deleted() {
        repo.save(user("user-1", "a@test.com"));
        repo.delete("user-1");

        assertTrue(repo.findByNeighbourhood("neighbourhood-1").isEmpty());
    }

    @Test
    void findByNeighbourhood_returns_empty_when_no_match() {
        assertTrue(repo.findByNeighbourhood("inexistant").isEmpty());
    }

    // ── findByRole ────────────────────────────────────────────────────────────

    @Test
    void findByRole_returns_users_with_role() {
        repo.save(userWithRole("user-1", "a@test.com", UserRole.admin));
        repo.save(userWithRole("user-2", "b@test.com", UserRole.admin));
        repo.save(userWithRole("user-3", "c@test.com", UserRole.resident));

        List<User> admins = repo.findByRole(UserRole.admin);
        assertEquals(2, admins.size());
    }

    @Test
    void findByRole_does_not_return_soft_deleted() {
        repo.save(userWithRole("user-1", "a@test.com", UserRole.admin));
        repo.delete("user-1");

        assertTrue(repo.findByRole(UserRole.admin).isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_user() {
        repo.save(user("user-1", "a@test.com"));
        assertTrue(repo.findById("user-1").isPresent());
    }

    @Test
    void save_updates_existing_user() {
        repo.save(user("user-1", "a@test.com"));

        User updated = new User(
                "user-1", "Updated", "Name", "new@test.com", "hash", null,
                null, "neighbourhood-1", Visibility.public_,
                null, MessagePolicy.open, "fr", null, null,
                UserRole.resident, null, null,
                Instant.now(), null, null
        );
        repo.save(updated);

        User found = repo.findById("user-1").orElseThrow();
        assertEquals("Updated",      found.firstName());
        assertEquals("new@test.com", found.email());
    }

    @Test
    void save_persists_all_enum_values() {
        User u = new User(
                "user-1", "A", "B", "a@test.com", "hash", null,
                null, null, Visibility.friends,
                null, MessagePolicy.filtered, "en", null, null,
                UserRole.moderator, null, null,
                Instant.now(), null, null
        );
        repo.save(u);

        User found = repo.findById("user-1").orElseThrow();
        assertEquals(Visibility.friends,       found.visibility());
        assertEquals(MessagePolicy.filtered,   found.messagePolicy());
        assertEquals(UserRole.moderator,       found.role());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sets_deleted_at() {
        repo.save(user("user-1", "a@test.com"));
        repo.delete("user-1");

        User found = repo.findById("user-1").orElseThrow();
        assertNotNull(found.deletedAt());
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("inexistant"));
    }
}