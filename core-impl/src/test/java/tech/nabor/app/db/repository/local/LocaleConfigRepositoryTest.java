package tech.nabor.app.db.repository.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.local.AppLocaleConfig;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocaleConfigRepositoryTest extends BaseRepositoryTest {

    private AppLocaleConfigRepository repo;
    private AppLocalAccountRepository accountRepo;

    @BeforeEach
    void setUp() {
        repo        = new AppLocaleConfigRepository(jdbi);
        accountRepo = new AppLocalAccountRepository(jdbi);

        accountRepo.save(new LocalAccount(
                "user-1", "myemail@example.com", "FirstName L", false, null
        ));
    }


    @Test
    void save_and_findByUserId() {
        AppLocaleConfig config = new AppLocaleConfig("user-1", "fr", Instant.now());

        repo.save(config);

        Optional<AppLocaleConfig> found = repo.findByUserId("user-1");
        assertTrue(found.isPresent());
        assertEquals("fr", found.get().locale());
    }

    @Test
    void save_updates_existing() {
        repo.save(new AppLocaleConfig("user-1", "fr", Instant.now()));
        repo.save(new AppLocaleConfig("user-1", "en", Instant.now()));

        AppLocaleConfig found = repo.findByUserId("user-1").orElseThrow();
        assertEquals("en", found.locale());
    }

    @Test
    void findByUserId_returns_empty_for_unknown_user() {
        assertTrue(repo.findByUserId("inexistant").isEmpty());
    }

    @Test
    void save_persists_updated_at() {
        Instant now = Instant.now();
        repo.save(new AppLocaleConfig("user-1", "fr", now));

        AppLocaleConfig found = repo.findByUserId("user-1").orElseThrow();
        // we compare in milliseconds because SQLite loses nanosecond precision
        assertEquals(now.toEpochMilli(), found.updatedAt().toEpochMilli());
    }
}