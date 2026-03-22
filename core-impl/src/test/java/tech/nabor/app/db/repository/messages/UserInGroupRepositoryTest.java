package tech.nabor.app.db.repository.messages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.messages.ChatGroup;
import tech.nabor.api.model.messages.UserInGroup;
import tech.nabor.api.model.enums.ChatGroupType;
import tech.nabor.api.model.enums.GroupRole;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserInGroupRepositoryTest extends BaseRepositoryTest {

    private AppUserInGroupRepository repo;
    private AppUserRepository userRepo;
    private AppChatGroupRepository groupRepo;

    @BeforeEach
    void setUp() {
        repo      = new AppUserInGroupRepository(jdbi);
        userRepo  = new AppUserRepository(jdbi);
        groupRepo = new AppChatGroupRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.basicUser("user-3", "c@test.com"));

        groupRepo.save(new ChatGroup("group-1", "G1", null, "user-1",
                ChatGroupType.group_chat, null, Instant.now(), null, null));
        groupRepo.save(new ChatGroup("group-2", "G2", null, "user-1",
                ChatGroupType.group_chat, null, Instant.now(), null, null));
    }

    private UserInGroup membership(String userId, String groupId, GroupRole role) {
        return new UserInGroup(userId, groupId, role, Instant.now(),
                null, null, false, null);
    }

    // ── findByGroupId ─────────────────────────────────────────────────────────

    @Test
    void findByGroupId_returns_active_members() {
        repo.save(membership("user-1", "group-1", GroupRole.admin));
        repo.save(membership("user-2", "group-1", GroupRole.message));

        List<UserInGroup> members = repo.findByGroupId("group-1");
        assertEquals(2, members.size());
    }

    @Test
    void findByGroupId_does_not_return_left_members() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.leave("user-1", "group-1");

        assertTrue(repo.findByGroupId("group-1").isEmpty());
    }

    @Test
    void findByGroupId_does_not_return_kicked_members() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.kick("user-1", "group-1");

        assertTrue(repo.findByGroupId("group-1").isEmpty());
    }

    @Test
    void findByGroupId_does_not_return_other_groups() {
        repo.save(membership("user-1", "group-2", GroupRole.message));

        assertTrue(repo.findByGroupId("group-1").isEmpty());
    }

    // ── findByUserId ──────────────────────────────────────────────────────────

    @Test
    void findByUserId_returns_active_memberships() {
        repo.save(membership("user-1", "group-1", GroupRole.admin));
        repo.save(membership("user-1", "group-2", GroupRole.message));

        List<UserInGroup> memberships = repo.findByUserId("user-1");
        assertEquals(2, memberships.size());
    }

    @Test
    void findByUserId_does_not_return_left_groups() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.leave("user-1", "group-1");

        assertTrue(repo.findByUserId("user-1").isEmpty());
    }

    // ── findByUserAndGroup ────────────────────────────────────────────────────

    @Test
    void findByUserAndGroup_returns_membership_when_exists() {
        repo.save(membership("user-1", "group-1", GroupRole.admin));

        Optional<UserInGroup> found = repo.findByUserAndGroup("user-1", "group-1");
        assertTrue(found.isPresent());
        assertEquals(GroupRole.admin, found.get().roleInGroup());
    }

    @Test
    void findByUserAndGroup_returns_empty_when_not_found() {
        assertTrue(repo.findByUserAndGroup("user-1", "group-1").isEmpty());
    }

    // ── isMember ──────────────────────────────────────────────────────────────

    @Test
    void isMember_returns_true_when_active_member() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        assertTrue(repo.isMember("user-1", "group-1"));
    }

    @Test
    void isMember_returns_false_when_not_member() {
        assertFalse(repo.isMember("user-1", "group-1"));
    }

    @Test
    void isMember_returns_false_after_leaving() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.leave("user-1", "group-1");

        assertFalse(repo.isMember("user-1", "group-1"));
    }

    @Test
    void isMember_returns_false_after_kick() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.kick("user-1", "group-1");

        assertFalse(repo.isMember("user-1", "group-1"));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_membership() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        assertTrue(repo.isMember("user-1", "group-1"));
    }

    @Test
    void save_updates_existing_membership() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.save(membership("user-1", "group-1", GroupRole.admin));

        UserInGroup found = repo.findByUserAndGroup("user-1", "group-1").orElseThrow();
        assertEquals(GroupRole.admin, found.roleInGroup());
    }

    @Test
    void save_persists_muted_status() {
        UserInGroup muted = new UserInGroup("user-1", "group-1", GroupRole.message,
                Instant.now(), null, null, true, Instant.now().plusSeconds(3600));
        repo.save(muted);

        UserInGroup found = repo.findByUserAndGroup("user-1", "group-1").orElseThrow();
        assertTrue(found.isMuted());
        assertNotNull(found.mutedUntil());
    }

    // ── leave ─────────────────────────────────────────────────────────────────

    @Test
    void leave_sets_left_at() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.leave("user-1", "group-1");

        UserInGroup found = repo.findByUserAndGroup("user-1", "group-1").orElseThrow();
        assertNotNull(found.leftAt());
    }

    @Test
    void leave_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.leave("user-1", "group-1"));
    }

    // ── kick ──────────────────────────────────────────────────────────────────

    @Test
    void kick_sets_kicked_at() {
        repo.save(membership("user-1", "group-1", GroupRole.message));
        repo.kick("user-1", "group-1");

        UserInGroup found = repo.findByUserAndGroup("user-1", "group-1").orElseThrow();
        assertNotNull(found.kickedAt());
    }

    @Test
    void kick_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.kick("user-1", "group-1"));
    }
}