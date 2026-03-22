package tech.nabor.app.db.repository.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.social.UserSwipe;
import tech.nabor.api.model.enums.SwipeDirection;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserSwipeRepositoryTest extends BaseRepositoryTest {

    private AppUserSwipeRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppUserSwipeRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.basicUser("user-3", "c@test.com"));
    }

    private UserSwipe swipe(String swiperId, String swipedId, SwipeDirection direction) {
        return new UserSwipe(swiperId, swipedId, direction, Instant.now());
    }

    // ── findBySwiperAndDirection ──────────────────────────────────────────────

    @Test
    void findBySwiperAndDirection_returns_matching_swipes() {
        repo.save(swipe("user-1", "user-2", SwipeDirection.like));
        repo.save(swipe("user-1", "user-3", SwipeDirection.dislike));

        List<UserSwipe> likes = repo.findBySwiperAndDirection("user-1", SwipeDirection.like);
        assertEquals(1, likes.size());
        assertEquals("user-2", likes.get(0).swipedId());
    }

    @Test
    void findBySwiperAndDirection_returns_empty_when_no_match() {
        assertTrue(repo.findBySwiperAndDirection("user-1", SwipeDirection.like).isEmpty());
    }

    @Test
    void findBySwiperAndDirection_does_not_return_other_directions() {
        repo.save(swipe("user-1", "user-2", SwipeDirection.dislike));

        assertTrue(repo.findBySwiperAndDirection("user-1", SwipeDirection.like).isEmpty());
    }

    @Test
    void findBySwiperAndDirection_does_not_return_other_swipers() {
        repo.save(swipe("user-2", "user-1", SwipeDirection.like));

        assertTrue(repo.findBySwiperAndDirection("user-1", SwipeDirection.like).isEmpty());
    }

    // ── findByPair ────────────────────────────────────────────────────────────

    @Test
    void findByPair_returns_swipe_when_exists() {
        repo.save(swipe("user-1", "user-2", SwipeDirection.like));

        Optional<UserSwipe> found = repo.findByPair("user-1", "user-2");
        assertTrue(found.isPresent());
        assertEquals(SwipeDirection.like, found.get().direction());
    }

    @Test
    void findByPair_returns_empty_when_not_found() {
        assertTrue(repo.findByPair("user-1", "user-2").isEmpty());
    }

    @Test
    void findByPair_is_not_symmetric() {
        repo.save(swipe("user-1", "user-2", SwipeDirection.like));
        assertTrue(repo.findByPair("user-2", "user-1").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_swipe() {
        repo.save(swipe("user-1", "user-2", SwipeDirection.like));
        assertTrue(repo.findByPair("user-1", "user-2").isPresent());
    }

    @Test
    void save_updates_existing_swipe() {
        repo.save(swipe("user-1", "user-2", SwipeDirection.like));
        repo.save(swipe("user-1", "user-2", SwipeDirection.dislike));

        UserSwipe found = repo.findByPair("user-1", "user-2").orElseThrow();
        assertEquals(SwipeDirection.dislike, found.direction());
        assertEquals(1, repo.findBySwiperAndDirection("user-1", SwipeDirection.dislike).size());
    }

    @Test
    void save_persists_direction() {
        repo.save(swipe("user-1", "user-2", SwipeDirection.dislike));

        UserSwipe found = repo.findByPair("user-1", "user-2").orElseThrow();
        assertEquals(SwipeDirection.dislike, found.direction());
    }
}