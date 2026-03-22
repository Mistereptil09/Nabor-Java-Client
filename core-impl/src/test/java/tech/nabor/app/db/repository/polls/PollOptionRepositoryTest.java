package tech.nabor.app.db.repository.polls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.polls.Poll;
import tech.nabor.api.model.polls.PollOption;
import tech.nabor.api.model.enums.PollType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PollOptionRepositoryTest extends BaseRepositoryTest {

    private AppPollOptionRepository repo;
    private AppUserRepository userRepo;
    private AppPollRepository pollRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppPollOptionRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);
        pollRepo = new AppPollRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));

        pollRepo.save(new Poll("poll-1", "Sondage 1", null, "user-1", "n-1",
                PollType.single, null, null, false, null, null, Instant.now(), null, null));
        pollRepo.save(new Poll("poll-2", "Sondage 2", null, "user-1", "n-1",
                PollType.single, null, null, false, null, null, Instant.now(), null, null));
    }

    private PollOption option(String id, String pollId, String label) {
        return new PollOption(id, pollId, label, Instant.now());
    }

    // ── findByPollId ──────────────────────────────────────────────────────────

    @Test
    void findByPollId_returns_options_for_poll() {
        repo.save(option("opt-1", "poll-1", "Oui"));
        repo.save(option("opt-2", "poll-1", "Non"));
        repo.save(option("opt-3", "poll-2", "Peut-être"));

        assertEquals(2, repo.findByPollId("poll-1").size());
    }

    @Test
    void findByPollId_returns_empty_when_no_options() {
        assertTrue(repo.findByPollId("poll-1").isEmpty());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_option_when_exists() {
        repo.save(option("opt-1", "poll-1", "Oui"));

        Optional<PollOption> found = repo.findById("opt-1");
        assertTrue(found.isPresent());
        assertEquals("Oui", found.get().label());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_option() {
        repo.save(option("opt-1", "poll-1", "Oui"));
        assertEquals(1, repo.findByPollId("poll-1").size());
    }

    @Test
    void save_updates_existing_option() {
        repo.save(option("opt-1", "poll-1", "Oui"));
        repo.save(option("opt-1", "poll-1", "Absolument"));

        PollOption found = repo.findById("opt-1").orElseThrow();
        assertEquals("Absolument", found.label());
        assertEquals(1, repo.findByPollId("poll-1").size()); // pas de doublon
    }

    // ── deleteByPollId ────────────────────────────────────────────────────────

    @Test
    void deleteByPollId_removes_all_options_for_poll() {
        repo.save(option("opt-1", "poll-1", "Oui"));
        repo.save(option("opt-2", "poll-1", "Non"));
        repo.save(option("opt-3", "poll-2", "Peut-être"));

        repo.deleteByPollId("poll-1");

        assertTrue(repo.findByPollId("poll-1").isEmpty());
        assertEquals(1, repo.findByPollId("poll-2").size());
    }

    @Test
    void deleteByPollId_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.deleteByPollId("inexistant"));
    }
}