package tech.nabor.app.db.repository.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.social.Follow;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FollowRepositoryTest extends BaseRepositoryTest {

    private AppFollowRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppFollowRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.basicUser("user-3", "c@test.com"));
    }

    private Follow follow(String followerId, String followedId) {
        return new Follow(followerId, followedId, Instant.now());
    }

    // ── findFollowersByUserId ─────────────────────────────────────────────────

    @Test
    void findFollowersByUserId_returns_users_following_target() {
        repo.save(follow("user-1", "user-2"));
        repo.save(follow("user-3", "user-2"));

        List<Follow> followers = repo.findFollowersByUserId("user-2");
        assertEquals(2, followers.size());
    }

    @Test
    void findFollowersByUserId_returns_empty_when_no_followers() {
        assertTrue(repo.findFollowersByUserId("user-1").isEmpty());
    }

    @Test
    void findFollowersByUserId_does_not_return_following() {
        repo.save(follow("user-1", "user-2")); // user-1 suit user-2
        repo.save(follow("user-1", "user-3")); // user-1 suit user-3

        // user-1 n'a aucun follower
        assertTrue(repo.findFollowersByUserId("user-1").isEmpty());
    }

    // ── findFollowingByUserId ─────────────────────────────────────────────────

    @Test
    void findFollowingByUserId_returns_users_followed_by_target() {
        repo.save(follow("user-1", "user-2"));
        repo.save(follow("user-1", "user-3"));

        List<Follow> following = repo.findFollowingByUserId("user-1");
        assertEquals(2, following.size());
    }

    @Test
    void findFollowingByUserId_returns_empty_when_not_following_anyone() {
        assertTrue(repo.findFollowingByUserId("user-1").isEmpty());
    }

    @Test
    void findFollowingByUserId_does_not_return_followers() {
        repo.save(follow("user-2", "user-1"));
        repo.save(follow("user-3", "user-1"));

        assertTrue(repo.findFollowingByUserId("user-1").isEmpty());
    }

    // ── isFollowing ───────────────────────────────────────────────────────────

    @Test
    void isFollowing_returns_true_when_following() {
        repo.save(follow("user-1", "user-2"));
        assertTrue(repo.isFollowing("user-1", "user-2"));
    }

    @Test
    void isFollowing_returns_false_when_not_following() {
        assertFalse(repo.isFollowing("user-1", "user-2"));
    }

    @Test
    void isFollowing_is_not_symmetric() {
        repo.save(follow("user-1", "user-2"));
        assertFalse(repo.isFollowing("user-2", "user-1"));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_follow() {
        repo.save(follow("user-1", "user-2"));
        assertTrue(repo.isFollowing("user-1", "user-2"));
    }

    @Test
    void save_updates_existing_follow() {
        repo.save(follow("user-1", "user-2"));
        Instant later = Instant.now().plusSeconds(10);
        repo.save(new Follow("user-1", "user-2", later));

        List<Follow> following = repo.findFollowingByUserId("user-1");
        assertEquals(1, following.size()); // pas de doublon
        assertEquals(later.toEpochMilli(), following.get(0).followedAt().toEpochMilli());
    }

    @Test
    void save_persists_followed_at() {
        Instant now = Instant.now();
        repo.save(new Follow("user-1", "user-2", now));

        Follow found = repo.findFollowingByUserId("user-1").get(0);
        assertEquals(now.toEpochMilli(), found.followedAt().toEpochMilli());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_follow() {
        repo.save(follow("user-1", "user-2"));
        repo.delete("user-1", "user-2");

        assertFalse(repo.isFollowing("user-1", "user-2"));
    }

    @Test
    void delete_only_removes_target_follow() {
        repo.save(follow("user-1", "user-2"));
        repo.save(follow("user-1", "user-3"));

        repo.delete("user-1", "user-2");

        assertFalse(repo.isFollowing("user-1", "user-2"));
        assertTrue(repo.isFollowing("user-1", "user-3"));
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("user-1", "user-2"));
    }
}