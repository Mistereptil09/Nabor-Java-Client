package tech.nabor.app.db.repository.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EvenementRepositoryTest extends BaseRepositoryTest {

    private AppEvenementRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppEvenementRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
    }

    private Evenement evenement(String id, String creatorId, EventStatus status,
                                String neighbourhoodId, Instant startsAt) {
        return new Evenement(id, creatorId, neighbourhoodId, null, null,
                "Test Event", status, null, 0, startsAt,
                startsAt != null ? startsAt.plusSeconds(3600) : null,
                null, 48, null, null, null, null,
                Instant.now(), null, null);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_evenement_when_exists() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));

        Optional<Evenement> found = repo.findById("event-1");
        assertTrue(found.isPresent());
        assertEquals("event-1", found.get().id());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    @Test
    void findById_returns_soft_deleted() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));
        repo.delete("event-1");

        assertTrue(repo.findById("event-1").isPresent());
        assertNotNull(repo.findById("event-1").get().deletedAt());
    }

    // ── findByCreatorId ───────────────────────────────────────────────────────

    @Test
    void findByCreatorId_returns_evenements_for_creator() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));
        repo.save(evenement("event-2", "user-1", EventStatus.open, "n-1", null));
        repo.save(evenement("event-3", "user-2", EventStatus.open, "n-1", null));

        assertEquals(2, repo.findByCreatorId("user-1").size());
    }

    @Test
    void findByCreatorId_does_not_return_soft_deleted() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));
        repo.delete("event-1");

        assertTrue(repo.findByCreatorId("user-1").isEmpty());
    }

    // ── findByNeighbourhood ───────────────────────────────────────────────────

    @Test
    void findByNeighbourhood_returns_matching_evenements() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));
        repo.save(evenement("event-2", "user-1", EventStatus.open, "n-1", null));
        repo.save(evenement("event-3", "user-1", EventStatus.open, "n-2", null));

        assertEquals(2, repo.findByNeighbourhood("n-1", EventStatus.open, 10).size());
    }

    @Test
    void findByNeighbourhood_filters_by_status() {
        repo.save(evenement("event-1", "user-1", EventStatus.open,      "n-1", null));
        repo.save(evenement("event-2", "user-1", EventStatus.cancelled, "n-1", null));

        assertEquals(1, repo.findByNeighbourhood("n-1", EventStatus.open, 10).size());
    }

    @Test
    void findByNeighbourhood_respects_limit() {
        for (int i = 1; i <= 5; i++) {
            repo.save(evenement("event-" + i, "user-1", EventStatus.open, "n-1", null));
        }
        assertEquals(3, repo.findByNeighbourhood("n-1", EventStatus.open, 3).size());
    }

    // ── findUpcoming ──────────────────────────────────────────────────────────

    @Test
    void findUpcoming_returns_future_open_events() {
        Instant future = Instant.now().plusSeconds(3600);
        Instant past   = Instant.now().minusSeconds(3600);

        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", future));
        repo.save(evenement("event-2", "user-1", EventStatus.open, "n-1", past));

        List<Evenement> upcoming = repo.findUpcoming("n-1", 10);
        assertEquals(1, upcoming.size());
        assertEquals("event-1", upcoming.getFirst().id());
    }

    @Test
    void findUpcoming_does_not_return_non_open_events() {
        Instant future = Instant.now().plusSeconds(3600);
        repo.save(evenement("event-1", "user-1", EventStatus.draft,     "n-1", future));
        repo.save(evenement("event-2", "user-1", EventStatus.cancelled, "n-1", future));

        assertTrue(repo.findUpcoming("n-1", 10).isEmpty());
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Test
    void findByStatus_returns_matching_evenements() {
        repo.save(evenement("event-1", "user-1", EventStatus.open,   "n-1", null));
        repo.save(evenement("event-2", "user-1", EventStatus.open,   "n-1", null));
        repo.save(evenement("event-3", "user-1", EventStatus.draft,  "n-1", null));

        assertEquals(2, repo.findByStatus(EventStatus.open, 10).size());
    }

    @Test
    void findByStatus_respects_limit() {
        for (int i = 1; i <= 5; i++) {
            repo.save(evenement("event-" + i, "user-1", EventStatus.open, "n-1", null));
        }
        assertEquals(2, repo.findByStatus(EventStatus.open, 2).size());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_evenement() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));
        assertTrue(repo.findById("event-1").isPresent());
    }

    @Test
    void save_updates_existing_evenement() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));

        Evenement updated = new Evenement("event-1", "user-1", "n-1", null, null,
                "Updated Title", EventStatus.cancelled, null, 500, null, null,
                null, 48, null, null, Instant.now(), null,
                Instant.now(), Instant.now(), null);
        repo.save(updated);

        Evenement found = repo.findById("event-1").orElseThrow();
        assertEquals("Updated Title",        found.title());
        assertEquals(EventStatus.cancelled,  found.status());
        assertEquals(500,                    found.costCents());
    }

    @Test
    void save_persists_all_statuses() {
        repo.save(evenement("e-1", "user-1", EventStatus.draft,     "n-1", null));
        repo.save(evenement("e-2", "user-1", EventStatus.published, "n-1", null));
        repo.save(evenement("e-3", "user-1", EventStatus.open,      "n-1", null));
        repo.save(evenement("e-4", "user-1", EventStatus.cancelled, "n-1", null));
        repo.save(evenement("e-5", "user-1", EventStatus.completed, "n-1", null));

        assertEquals(EventStatus.draft,     repo.findById("e-1").orElseThrow().status());
        assertEquals(EventStatus.published, repo.findById("e-2").orElseThrow().status());
        assertEquals(EventStatus.open,      repo.findById("e-3").orElseThrow().status());
        assertEquals(EventStatus.cancelled, repo.findById("e-4").orElseThrow().status());
        assertEquals(EventStatus.completed, repo.findById("e-5").orElseThrow().status());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sets_deleted_at() {
        repo.save(evenement("event-1", "user-1", EventStatus.open, "n-1", null));
        repo.delete("event-1");

        assertNotNull(repo.findById("event-1").orElseThrow().deletedAt());
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("inexistant"));
    }
}