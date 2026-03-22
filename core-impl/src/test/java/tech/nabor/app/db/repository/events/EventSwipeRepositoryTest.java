package tech.nabor.app.db.repository.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.events.EventSwipe;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.api.model.enums.SwipeDirection;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EventSwipeRepositoryTest extends BaseRepositoryTest {

    private AppEventSwipeRepository repo;
    private AppUserRepository userRepo;
    private AppEvenementRepository evenementRepo;

    @BeforeEach
    void setUp() {
        repo          = new AppEventSwipeRepository(jdbi);
        userRepo      = new AppUserRepository(jdbi);
        evenementRepo = new AppEvenementRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));

        evenementRepo.save(new Evenement("event-1", "user-1", "n-1", null, null,
                "Event 1", EventStatus.open, null, 0, null, null,
                null, 48, null, null, null, null, Instant.now(), null, null));
        evenementRepo.save(new Evenement("event-2", "user-1", "n-1", null, null,
                "Event 2", EventStatus.open, null, 0, null, null,
                null, 48, null, null, null, null, Instant.now(), null, null));
    }

    private EventSwipe swipe(String userId, String eventId, SwipeDirection direction) {
        return new EventSwipe(userId, eventId, direction, Instant.now());
    }

    // ── findByUserAndEvent ────────────────────────────────────────────────────

    @Test
    void findByUserAndEvent_returns_swipe_when_exists() {
        repo.save(swipe("user-1", "event-1", SwipeDirection.like));

        Optional<EventSwipe> found = repo.findByUserAndEvent("user-1", "event-1");
        assertTrue(found.isPresent());
        assertEquals(SwipeDirection.like, found.get().direction());
    }

    @Test
    void findByUserAndEvent_returns_empty_when_not_found() {
        assertTrue(repo.findByUserAndEvent("user-1", "event-1").isEmpty());
    }

    // ── findByUserAndDirection ────────────────────────────────────────────────

    @Test
    void findByUserAndDirection_returns_matching_swipes() {
        repo.save(swipe("user-1", "event-1", SwipeDirection.like));
        repo.save(swipe("user-1", "event-2", SwipeDirection.dislike));

        List<EventSwipe> likes = repo.findByUserAndDirection("user-1", SwipeDirection.like);
        assertEquals(1, likes.size());
        assertEquals("event-1", likes.getFirst().eventId());
    }

    @Test
    void findByUserAndDirection_returns_empty_when_no_match() {
        assertTrue(repo.findByUserAndDirection("user-1", SwipeDirection.like).isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_swipe() {
        repo.save(swipe("user-1", "event-1", SwipeDirection.like));
        assertTrue(repo.findByUserAndEvent("user-1", "event-1").isPresent());
    }

    @Test
    void save_updates_existing_swipe() {
        repo.save(swipe("user-1", "event-1", SwipeDirection.like));
        repo.save(swipe("user-1", "event-1", SwipeDirection.dislike));

        EventSwipe found = repo.findByUserAndEvent("user-1", "event-1").orElseThrow();
        assertEquals(SwipeDirection.dislike, found.direction());
    }
}