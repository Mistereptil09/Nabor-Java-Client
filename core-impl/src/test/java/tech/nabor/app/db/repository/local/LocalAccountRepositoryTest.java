// core-impl/src/test/java/tech/nabor/app/db/repository/local/LocalAccountRepositoryTest.java
package tech.nabor.app.db.repository.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalAccountRepositoryTest extends BaseRepositoryTest {

    private AppLocalAccountRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AppLocalAccountRepository(jdbi);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_account_when_exists() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "antonio@example.com", "Antonio B"));

        Optional<LocalAccount> found = repo.findById("user-1");

        assertTrue(found.isPresent());
        assertEquals("user-1",              found.get().userId());
        assertEquals("antonio@example.com", found.get().email());
        assertEquals("Antonio B",           found.get().displayName());
        assertFalse(found.get().isActive());
        assertNull(found.get().lastLoginAt());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returns_empty_list_when_no_accounts() {
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void findAll_returns_all_accounts() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));
        repo.save(LocalAccountFixtures.basicLocalAccount("user-2", "b@test.com", "User B"));

        assertEquals(2, repo.findAll().size());
    }

    @Test
    void findAll_ordered_by_last_login_desc() {
        Instant earlier = Instant.parse("2026-01-01T10:00:00Z");
        Instant later   = Instant.parse("2026-03-01T10:00:00Z");

        repo.save(new LocalAccount("user-1", "a@test.com", "User A", false, earlier));
        repo.save(new LocalAccount("user-1", "a@test.com", "User A", false, earlier));
        repo.save(new LocalAccount("user-2", "b@test.com", "User B", false, later));

        List<LocalAccount> all = repo.findAll();
        assertEquals("user-2", all.get(0).userId()); // plus récent en premier
        assertEquals("user-1", all.get(1).userId());
    }

    @Test
    void findAll_accounts_with_null_login_appear_last() {
        Instant recent = Instant.parse("2026-03-01T10:00:00Z");

        repo.save(new LocalAccount("user-1", "a@test.com", "User A", false, null));
        repo.save(new LocalAccount("user-2", "b@test.com", "User B", false, recent));

        List<LocalAccount> all = repo.findAll();
        assertEquals("user-2", all.get(0).userId());
        assertEquals("user-1", all.get(1).userId());
    }

    // ── findActive ────────────────────────────────────────────────────────────

    @Test
    void findActive_returns_empty_when_no_active_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));

        assertTrue(repo.findActive().isEmpty());
    }

    @Test
    void findActive_returns_active_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));
        repo.save(LocalAccountFixtures.basicLocalAccount("user-2", "b@test.com", "User B"));
        repo.setActive("user-1");

        Optional<LocalAccount> active = repo.findActive();
        assertTrue(active.isPresent());
        assertEquals("user-1", active.get().userId());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));

        assertEquals(1, repo.findAll().size());
    }

    @Test
    void save_updates_existing_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "old@test.com", "Old Name"));
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "new@test.com", "New Name"));

        LocalAccount found = repo.findById("user-1").orElseThrow();
        assertEquals("new@test.com", found.email());
        assertEquals("New Name",     found.displayName());
        assertEquals(1, repo.findAll().size()); // pas de doublon
    }

    @Test
    void save_persists_last_login_at() {
        Instant now = Instant.now();
        repo.save(new LocalAccount("user-1", "a@test.com", "User A", false, now));

        LocalAccount found = repo.findById("user-1").orElseThrow();
        // comparaison en millisecondes — SQLite perd la précision nanoseconde
        assertEquals(now.toEpochMilli(), found.lastLoginAt().toEpochMilli());
    }

    @Test
    void save_persists_is_active_true() {
        repo.save(new LocalAccount("user-1", "a@test.com", "User A", true, null));

        assertTrue(repo.findById("user-1").orElseThrow().isActive());
    }

    // ── setActive ─────────────────────────────────────────────────────────────

    @Test
    void setActive_activates_target_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));
        repo.setActive("user-1");

        assertTrue(repo.findById("user-1").orElseThrow().isActive());
    }

    @Test
    void setActive_deactivates_previously_active_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));
        repo.save(LocalAccountFixtures.basicLocalAccount("user-2", "b@test.com", "User B"));

        repo.setActive("user-1");
        repo.setActive("user-2");

        assertFalse(repo.findById("user-1").orElseThrow().isActive());
        assertTrue(repo.findById("user-2").orElseThrow().isActive());
    }

    @Test
    void setActive_only_one_active_at_a_time() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));
        repo.save(LocalAccountFixtures.basicLocalAccount("user-2", "b@test.com", "User B"));
        repo.save(LocalAccountFixtures.basicLocalAccount("user-3", "c@test.com", "User C"));

        repo.setActive("user-2");

        long activeCount = repo.findAll().stream()
                .filter(LocalAccount::isActive)
                .count();
        assertEquals(1, activeCount);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));
        repo.delete("user-1");

        assertTrue(repo.findById("user-1").isEmpty());
    }

    @Test
    void delete_nonexistent_account_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("inexistant"));
    }

    @Test
    void delete_only_removes_target_account() {
        repo.save(LocalAccountFixtures.basicLocalAccount("user-1", "a@test.com", "User A"));
        repo.save(LocalAccountFixtures.basicLocalAccount("user-2", "b@test.com", "User B"));

        repo.delete("user-1");

        assertTrue(repo.findById("user-1").isEmpty());
        assertTrue(repo.findById("user-2").isPresent());
    }
}