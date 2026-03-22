package tech.nabor.app.db.repository.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.social.Friendship;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FriendshipRepositoryTest extends BaseRepositoryTest {

    private AppFriendshipRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppFriendshipRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.basicUser("user-3", "c@test.com"));
    }

    private Friendship friendship(String id, String user1Id, String user2Id) {
        // respecte la convention user1_id < user2_id
        String a = user1Id.compareTo(user2Id) < 0 ? user1Id : user2Id;
        String b = user1Id.compareTo(user2Id) < 0 ? user2Id : user1Id;
        return new Friendship(id, a, b, Instant.now(), null, null);
    }

    // ── findByUserId ──────────────────────────────────────────────────────────

    @Test
    void findByUserId_returns_active_friendships() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        repo.save(friendship("f-2", "user-1", "user-3"));

        List<Friendship> result = repo.findByUserId("user-1");
        assertEquals(2, result.size());
    }

    @Test
    void findByUserId_returns_empty_when_no_friendships() {
        assertTrue(repo.findByUserId("user-1").isEmpty());
    }

    @Test
    void findByUserId_does_not_return_unfriended() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        repo.delete("f-1");

        assertTrue(repo.findByUserId("user-1").isEmpty());
    }

    @Test
    void findByUserId_works_for_both_user1_and_user2() {
        repo.save(friendship("f-1", "user-1", "user-2"));

        assertFalse(repo.findByUserId("user-1").isEmpty());
        assertFalse(repo.findByUserId("user-2").isEmpty());
    }

    // ── findByPair ────────────────────────────────────────────────────────────

    @Test
    void findByPair_returns_friendship_regardless_of_order() {
        repo.save(friendship("f-1", "user-1", "user-2"));

        assertTrue(repo.findByPair("user-1", "user-2").isPresent());
        assertTrue(repo.findByPair("user-2", "user-1").isPresent());
    }

    @Test
    void findByPair_returns_empty_when_not_found() {
        assertTrue(repo.findByPair("user-1", "user-2").isEmpty());
    }

    // ── areFriends ────────────────────────────────────────────────────────────

    @Test
    void areFriends_returns_true_when_active_friendship() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        assertTrue(repo.areFriends("user-1", "user-2"));
    }

    @Test
    void areFriends_returns_false_when_no_friendship() {
        assertFalse(repo.areFriends("user-1", "user-2"));
    }

    @Test
    void areFriends_returns_false_after_unfriending() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        repo.delete("f-1");

        assertFalse(repo.areFriends("user-1", "user-2"));
    }

    @Test
    void areFriends_is_symmetric() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        assertTrue(repo.areFriends("user-1", "user-2"));
        assertTrue(repo.areFriends("user-2", "user-1"));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_friendship() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        assertTrue(repo.findByPair("user-1", "user-2").isPresent());
    }

    @Test
    void save_updates_existing_friendship() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        repo.save(new Friendship("f-1", "user-1", "user-2",
                Instant.now(), null, "group-1"));

        Friendship found = repo.findByPair("user-1", "user-2").orElseThrow();
        assertEquals("group-1", found.groupId());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sets_unfriended_at() {
        repo.save(friendship("f-1", "user-1", "user-2"));
        repo.delete("f-1");

        Friendship found = repo.findByPair("user-1", "user-2").orElseThrow();
        assertNotNull(found.unfriendedAt());
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("inexistant"));
    }
}