package tech.nabor.app.db.repository.polls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.polls.Poll;
import tech.nabor.api.model.enums.PollType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PollRepositoryTest extends BaseRepositoryTest {

    private AppPollRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppPollRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
    }

    private Poll poll(String id, String creatorId, String neighbourhoodId) {
        return new Poll(id, "Sondage test", null, creatorId, neighbourhoodId,
                PollType.single, null, null, false, null, null,
                Instant.now(), null, null);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_poll_when_exists() {
        repo.save(poll("poll-1", "user-1", "n-1"));

        Optional<Poll> found = repo.findById("poll-1");
        assertTrue(found.isPresent());
        assertEquals("poll-1", found.get().id());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    @Test
    void findById_returns_soft_deleted() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.delete("poll-1");

        assertTrue(repo.findById("poll-1").isPresent());
        assertNotNull(repo.findById("poll-1").get().deletedAt());
    }

    // ── findByCreatorId ───────────────────────────────────────────────────────

    @Test
    void findByCreatorId_returns_polls_for_creator() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.save(poll("poll-2", "user-1", "n-1"));
        repo.save(poll("poll-3", "user-2", "n-1"));

        assertEquals(2, repo.findByCreatorId("user-1").size());
    }

    @Test
    void findByCreatorId_does_not_return_soft_deleted() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.delete("poll-1");

        assertTrue(repo.findByCreatorId("user-1").isEmpty());
    }

    // ── findByNeighbourhood ───────────────────────────────────────────────────

    @Test
    void findByNeighbourhood_returns_matching_polls() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.save(poll("poll-2", "user-1", "n-1"));
        repo.save(poll("poll-3", "user-1", "n-2"));

        assertEquals(2, repo.findByNeighbourhood("n-1", 10).size());
    }

    @Test
    void findByNeighbourhood_respects_limit() {
        for (int i = 1; i <= 5; i++) {
            repo.save(poll("poll-" + i, "user-1", "n-1"));
        }
        assertEquals(3, repo.findByNeighbourhood("n-1", 3).size());
    }

    // ── findActive ────────────────────────────────────────────────────────────

    @Test
    void findActive_returns_open_polls() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.save(poll("poll-2", "user-1", "n-1"));

        assertEquals(2, repo.findActive("n-1", 10).size());
    }

    @Test
    void findActive_does_not_return_closed_polls() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.close("poll-1", "user-1");

        assertTrue(repo.findActive("n-1", 10).isEmpty());
    }

    @Test
    void findActive_does_not_return_expired_polls() {
        Poll expired = new Poll("poll-1", "Sondage", null, "user-1", "n-1",
                PollType.single, null,
                Instant.now().minusSeconds(3600), // ends_at dans le passé
                false, null, null, Instant.now(), null, null);
        repo.save(expired);

        assertTrue(repo.findActive("n-1", 10).isEmpty());
    }

    @Test
    void findActive_returns_polls_without_end_date() {
        Poll noEndDate = new Poll("poll-1", "Sondage", null, "user-1", "n-1",
                PollType.single, null, null, false, null, null,
                Instant.now(), null, null);
        repo.save(noEndDate);

        assertEquals(1, repo.findActive("n-1", 10).size());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_poll() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        assertTrue(repo.findById("poll-1").isPresent());
    }

    @Test
    void save_updates_existing_poll() {
        repo.save(poll("poll-1", "user-1", "n-1"));

        Poll updated = new Poll("poll-1", "Titre modifié", "Description",
                "user-1", "n-1", PollType.multiple, null, null,
                true, null, null, Instant.now(), Instant.now(), null);
        repo.save(updated);

        Poll found = repo.findById("poll-1").orElseThrow();
        assertEquals("Titre modifié",    found.title());
        assertEquals(PollType.multiple,  found.pollType());
        assertTrue(found.isAnonymous());
    }

    @Test
    void save_persists_all_poll_types() {
        repo.save(new Poll("p-1", "T", null, "user-1", "n-1",
                PollType.single, null, null, false, null, null, Instant.now(), null, null));
        repo.save(new Poll("p-2", "T", null, "user-1", "n-1",
                PollType.multiple, null, null, false, null, null, Instant.now(), null, null));
        repo.save(new Poll("p-3", "T", null, "user-1", "n-1",
                PollType.weighted, null, null, false, null, null, Instant.now(), null, null));

        assertEquals(PollType.single,   repo.findById("p-1").orElseThrow().pollType());
        assertEquals(PollType.multiple, repo.findById("p-2").orElseThrow().pollType());
        assertEquals(PollType.weighted, repo.findById("p-3").orElseThrow().pollType());
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @Test
    void close_sets_closed_at_and_closed_by() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.close("poll-1", "user-1");

        Poll found = repo.findById("poll-1").orElseThrow();
        assertNotNull(found.closedAt());
        assertEquals("user-1", found.closedBy());
    }

    @Test
    void close_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.close("inexistant", "user-1"));
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sets_deleted_at() {
        repo.save(poll("poll-1", "user-1", "n-1"));
        repo.delete("poll-1");

        assertNotNull(repo.findById("poll-1").orElseThrow().deletedAt());
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("inexistant"));
    }
}