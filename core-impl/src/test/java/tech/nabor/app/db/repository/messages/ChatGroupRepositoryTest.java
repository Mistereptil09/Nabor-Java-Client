package tech.nabor.app.db.repository.messages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.messages.ChatGroup;
import tech.nabor.api.model.enums.ChatGroupType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChatGroupRepositoryTest extends BaseRepositoryTest {

    private AppChatGroupRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppChatGroupRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
    }

    private ChatGroup group(String id, String createdBy, ChatGroupType type) {
        return new ChatGroup(id, "Test Group", null, createdBy, type, null,
                Instant.now(), null, null);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_group_when_exists() {
        repo.save(group("group-1", "user-1", ChatGroupType.group_chat));

        Optional<ChatGroup> found = repo.findById("group-1");
        assertTrue(found.isPresent());
        assertEquals("group-1", found.get().id());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    @Test
    void findById_returns_soft_deleted_group() {
        repo.save(group("group-1", "user-1", ChatGroupType.group_chat));
        repo.delete("group-1");

        assertTrue(repo.findById("group-1").isPresent());
        assertNotNull(repo.findById("group-1").get().deletedAt());
    }

    // ── findByType ────────────────────────────────────────────────────────────

    @Test
    void findByType_returns_matching_groups() {
        repo.save(group("group-1", "user-1", ChatGroupType.group_chat));
        repo.save(group("group-2", "user-1", ChatGroupType.group_chat));
        repo.save(group("group-3", "user-1", ChatGroupType.direct_message));

        List<ChatGroup> result = repo.findByType(ChatGroupType.group_chat);
        assertEquals(2, result.size());
    }

    @Test
    void findByType_does_not_return_soft_deleted() {
        repo.save(group("group-1", "user-1", ChatGroupType.group_chat));
        repo.delete("group-1");

        assertTrue(repo.findByType(ChatGroupType.group_chat).isEmpty());
    }

    @Test
    void findByType_returns_empty_when_no_match() {
        assertTrue(repo.findByType(ChatGroupType.neighbourhood).isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_group() {
        repo.save(group("group-1", "user-1", ChatGroupType.group_chat));
        assertTrue(repo.findById("group-1").isPresent());
    }

    @Test
    void save_updates_existing_group() {
        repo.save(group("group-1", "user-1", ChatGroupType.group_chat));

        ChatGroup updated = new ChatGroup("group-1", "Updated Name", "desc",
                "user-1", ChatGroupType.group_chat, null, Instant.now(), Instant.now(), null);
        repo.save(updated);

        ChatGroup found = repo.findById("group-1").orElseThrow();
        assertEquals("Updated Name", found.name());
        assertEquals("desc",         found.description());
    }

    @Test
    void save_persists_all_types() {
        repo.save(group("group-1", "user-1", ChatGroupType.direct_message));
        repo.save(group("group-2", "user-1", ChatGroupType.group_chat));
        repo.save(group("group-3", "user-1", ChatGroupType.neighbourhood));

        assertEquals(ChatGroupType.direct_message, repo.findById("group-1").orElseThrow().type());
        assertEquals(ChatGroupType.group_chat,     repo.findById("group-2").orElseThrow().type());
        assertEquals(ChatGroupType.neighbourhood,  repo.findById("group-3").orElseThrow().type());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sets_deleted_at() {
        repo.save(group("group-1", "user-1", ChatGroupType.group_chat));
        repo.delete("group-1");

        assertNotNull(repo.findById("group-1").orElseThrow().deletedAt());
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("inexistant"));
    }
}