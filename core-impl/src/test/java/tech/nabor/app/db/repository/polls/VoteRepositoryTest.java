package tech.nabor.app.db.repository.polls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.polls.Poll;
import tech.nabor.api.model.polls.PollOption;
import tech.nabor.api.model.polls.Vote;
import tech.nabor.api.model.enums.PollType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VoteRepositoryTest extends BaseRepositoryTest {

    private AppVoteRepository repo;
    private AppUserRepository userRepo;
    private AppPollRepository pollRepo;
    private AppPollOptionRepository optionRepo;

    @BeforeEach
    void setUp() {
        repo       = new AppVoteRepository(jdbi);
        userRepo   = new AppUserRepository(jdbi);
        pollRepo   = new AppPollRepository(jdbi);
        optionRepo = new AppPollOptionRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));

        pollRepo.save(new Poll("poll-1", "Sondage", null, "user-1", "n-1",
                PollType.weighted, null, null, false, null, null, Instant.now(), null, null));

        optionRepo.save(new PollOption("opt-1", "poll-1", "Oui",  Instant.now()));
        optionRepo.save(new PollOption("opt-2", "poll-1", "Non",  Instant.now()));
        optionRepo.save(new PollOption("opt-3", "poll-1", "NSP",  Instant.now()));
    }

    private Vote vote(String userId, String optionId, int weight) {
        return new Vote(userId, optionId, weight, Instant.now(), null);
    }

    // ── findByOptionId ────────────────────────────────────────────────────────

    @Test
    void findByOptionId_returns_votes_for_option() {
        repo.save(vote("user-1", "opt-1", 1));
        repo.save(vote("user-2", "opt-1", 2));

        assertEquals(2, repo.findByOptionId("opt-1").size());
    }

    @Test
    void findByOptionId_returns_empty_when_no_votes() {
        assertTrue(repo.findByOptionId("opt-1").isEmpty());
    }

    @Test
    void findByOptionId_does_not_return_other_options() {
        repo.save(vote("user-1", "opt-2", 1));
        assertTrue(repo.findByOptionId("opt-1").isEmpty());
    }

    // ── findByUserAndPoll ─────────────────────────────────────────────────────

    @Test
    void findByUserAndPoll_returns_all_votes_for_user_in_poll() {
        repo.save(vote("user-1", "opt-1", 1));
        repo.save(vote("user-1", "opt-2", 2));
        repo.save(vote("user-2", "opt-1", 1));

        List<Vote> votes = repo.findByUserAndPoll("user-1", "poll-1");
        assertEquals(2, votes.size());
    }

    @Test
    void findByUserAndPoll_returns_empty_when_no_votes() {
        assertTrue(repo.findByUserAndPoll("user-1", "poll-1").isEmpty());
    }

    // ── findByUserAndOption ───────────────────────────────────────────────────

    @Test
    void findByUserAndOption_returns_vote_when_exists() {
        repo.save(vote("user-1", "opt-1", 3));

        Optional<Vote> found = repo.findByUserAndOption("user-1", "opt-1");
        assertTrue(found.isPresent());
        assertEquals(3, found.get().weight());
    }

    @Test
    void findByUserAndOption_returns_empty_when_not_found() {
        assertTrue(repo.findByUserAndOption("user-1", "opt-1").isEmpty());
    }

    // ── countByOptionId ───────────────────────────────────────────────────────

    @Test
    void countByOptionId_counts_votes_correctly() {
        repo.save(vote("user-1", "opt-1", 1));
        repo.save(vote("user-2", "opt-1", 1));

        assertEquals(2, repo.countByOptionId("opt-1"));
    }

    @Test
    void countByOptionId_returns_zero_when_no_votes() {
        assertEquals(0, repo.countByOptionId("opt-1"));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_vote() {
        repo.save(vote("user-1", "opt-1", 1));
        assertTrue(repo.findByUserAndOption("user-1", "opt-1").isPresent());
    }

    @Test
    void save_updates_existing_vote_weight() {
        repo.save(vote("user-1", "opt-1", 1));
        repo.save(new Vote("user-1", "opt-1", 5, Instant.now(), Instant.now()));

        Vote found = repo.findByUserAndOption("user-1", "opt-1").orElseThrow();
        assertEquals(5, found.weight());
        assertNotNull(found.updatedAt());
    }

    @Test
    void save_no_duplicate_vote() {
        repo.save(vote("user-1", "opt-1", 1));
        repo.save(vote("user-1", "opt-1", 2));

        assertEquals(1, repo.findByOptionId("opt-1").size());
    }

    // ── deleteByUserAndPoll ───────────────────────────────────────────────────

    @Test
    void deleteByUserAndPoll_removes_all_votes_for_user_in_poll() {
        repo.save(vote("user-1", "opt-1", 1));
        repo.save(vote("user-1", "opt-2", 2));
        repo.save(vote("user-2", "opt-1", 1));

        repo.deleteByUserAndPoll("user-1", "poll-1");

        assertTrue(repo.findByUserAndPoll("user-1", "poll-1").isEmpty());
        assertEquals(1, repo.findByOptionId("opt-1").size()); // user-2 reste
    }

    @Test
    void deleteByUserAndPoll_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.deleteByUserAndPoll("user-1", "poll-1"));
    }
}