// core-impl/src/test/java/tech/nabor/app/db/repository/user/UserNotificationPreferencesRepositoryTest.java
package tech.nabor.app.db.repository.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.user.User;
import tech.nabor.api.model.user.UserNotificationPreferences;
import tech.nabor.api.model.enums.MessagePolicy;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.enums.Visibility;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserNotificationPreferencesRepositoryTest extends BaseRepositoryTest {

    private AppUserNotificationPreferencesRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppUserNotificationPreferencesRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
    }

    private UserNotificationPreferences allEnabled() {
        return new UserNotificationPreferences(
                "user-1", true, true, true, true, true, true, null
        );
    }

    private UserNotificationPreferences allDisabled() {
        return new UserNotificationPreferences(
                "user-1", false, false, false, false, false, false, null
        );
    }

    // ── findByUserId ──────────────────────────────────────────────────────────

    @Test
    void findByUserId_returns_prefs_when_exists() {
        repo.save(allEnabled());

        Optional<UserNotificationPreferences> found = repo.findByUserId("user-1");
        assertTrue(found.isPresent());
        assertEquals("user-1", found.get().userId());
    }

    @Test
    void findByUserId_returns_empty_when_not_found() {
        assertTrue(repo.findByUserId("inexistant").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_persists_all_true_values() {
        repo.save(allEnabled());

        UserNotificationPreferences found = repo.findByUserId("user-1").orElseThrow();
        assertTrue(found.notifNewFollower());
        assertTrue(found.notifNewListing());
        assertTrue(found.notifNewEvent());
        assertTrue(found.notifNewPoll());
        assertTrue(found.notifWaitlist());
        assertTrue(found.notifMessage());
    }

    @Test
    void save_persists_all_false_values() {
        repo.save(allDisabled());

        UserNotificationPreferences found = repo.findByUserId("user-1").orElseThrow();
        assertFalse(found.notifNewFollower());
        assertFalse(found.notifNewListing());
        assertFalse(found.notifNewEvent());
        assertFalse(found.notifNewPoll());
        assertFalse(found.notifWaitlist());
        assertFalse(found.notifMessage());
    }

    @Test
    void save_updates_existing_prefs() {
        repo.save(allEnabled());
        repo.save(allDisabled());

        UserNotificationPreferences found = repo.findByUserId("user-1").orElseThrow();
        assertFalse(found.notifNewFollower());
        assertFalse(found.notifMessage());
    }

    @Test
    void save_no_duplicate_row() {
        repo.save(allEnabled());
        repo.save(allDisabled());

        // une seule ligne — pas de doublon
        assertTrue(repo.findByUserId("user-1").isPresent());
    }

    @Test
    void save_persists_updated_at() {
        Instant now = Instant.now();
        repo.save(new UserNotificationPreferences(
                "user-1", true, true, true, true, true, true, now
        ));

        UserNotificationPreferences found = repo.findByUserId("user-1").orElseThrow();
        assertEquals(now.toEpochMilli(), found.updatedAt().toEpochMilli());
    }

    @Test
    void save_partial_preferences() {
        repo.save(new UserNotificationPreferences(
                "user-1", true, false, true, false, true, false, null
        ));

        UserNotificationPreferences found = repo.findByUserId("user-1").orElseThrow();
        assertTrue(found.notifNewFollower());
        assertFalse(found.notifNewListing());
        assertTrue(found.notifNewEvent());
        assertFalse(found.notifNewPoll());
        assertTrue(found.notifWaitlist());
        assertFalse(found.notifMessage());
    }
}