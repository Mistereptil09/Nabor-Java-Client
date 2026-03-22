package tech.nabor.app.db.repository.messages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.messages.ChatGroup;
import tech.nabor.api.model.messages.MessageMetadata;
import tech.nabor.api.model.enums.ChatGroupType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MessageMetadataRepositoryTest extends BaseRepositoryTest {

    private AppMessageMetadataRepository repo;
    private AppUserRepository userRepo;
    private AppChatGroupRepository groupRepo;

    @BeforeEach
    void setUp() {
        repo = new AppMessageMetadataRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);
        groupRepo = new AppChatGroupRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        groupRepo.save(new ChatGroup("group-1", "G1", null, "user-1",
                ChatGroupType.group_chat, null, Instant.now(), null, null));
    }

    private MessageMetadata message(String id, String groupId, String senderId, Instant sentAt) {
        return new MessageMetadata(id, "mongo-" + id, groupId, senderId,
                sentAt, null, false, null, null);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_message_when_exists() {
        repo.save(message("msg-1", "group-1", "user-1", Instant.now()));

        Optional<MessageMetadata> found = repo.findById("msg-1");
        assertTrue(found.isPresent());
        assertEquals("msg-1", found.get().id());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    // ── findByGroupId ─────────────────────────────────────────────────────────

    @Test
    void findByGroupId_returns_messages_ordered_desc() {
        Instant t1 = Instant.now().minusSeconds(20);
        Instant t2 = Instant.now().minusSeconds(10);
        Instant t3 = Instant.now();

        repo.save(message("msg-1", "group-1", "user-1", t1));
        repo.save(message("msg-2", "group-1", "user-1", t2));
        repo.save(message("msg-3", "group-1", "user-1", t3));

        List<MessageMetadata> result = repo.findByGroupId("group-1", 10);
        assertEquals("msg-3", result.get(0).id()); // plus récent en premier
        assertEquals("msg-1", result.get(2).id());
    }

    @Test
    void findByGroupId_respects_limit() {
        for (int i = 1; i <= 5; i++) {
            repo.save(message("msg-" + i, "group-1", "user-1",
                    Instant.now().minusSeconds(10 - i)));
        }

        assertEquals(3, repo.findByGroupId("group-1", 3).size());
    }

    @Test
    void findByGroupId_does_not_return_deleted_messages() {
        repo.save(message("msg-1", "group-1", "user-1", Instant.now()));
        repo.markDeleted("msg-1");

        assertTrue(repo.findByGroupId("group-1", 10).isEmpty());
    }

    // ── findByGroupIdBefore ───────────────────────────────────────────────────

    @Test
    void findByGroupIdBefore_returns_messages_before_timestamp() {
        Instant t1 = Instant.now().minusSeconds(30);
        Instant t2 = Instant.now().minusSeconds(20);
        Instant t3 = Instant.now().minusSeconds(10);
        Instant cutoff = Instant.now().minusSeconds(15);

        repo.save(message("msg-1", "group-1", "user-1", t1));
        repo.save(message("msg-2", "group-1", "user-1", t2));
        repo.save(message("msg-3", "group-1", "user-1", t3));

        List<MessageMetadata> result = repo.findByGroupIdBefore("group-1", cutoff, 10);
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(m -> m.id().equals("msg-3")));
    }

    // ── findThreadByParentId ──────────────────────────────────────────────────

    @Test
    void findThreadByParentId_returns_replies() {
        repo.save(message("msg-1", "group-1", "user-1", Instant.now().minusSeconds(10)));

        MessageMetadata reply1 = new MessageMetadata("reply-1", "mongo-r1", "group-1",
                "user-2", Instant.now().minusSeconds(5), null, false, null, "msg-1");
        MessageMetadata reply2 = new MessageMetadata("reply-2", "mongo-r2", "group-1",
                "user-1", Instant.now(), null, false, null, "msg-1");

        repo.save(reply1);
        repo.save(reply2);

        List<MessageMetadata> thread = repo.findThreadByParentId("msg-1");
        assertEquals(2, thread.size());
        assertEquals("reply-1", thread.getFirst().id()); // order ASC
    }

    @Test
    void findThreadByParentId_returns_empty_when_no_replies() {
        repo.save(message("msg-1", "group-1", "user-1", Instant.now()));
        assertTrue(repo.findThreadByParentId("msg-1").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_message() {
        repo.save(message("msg-1", "group-1", "user-1", Instant.now()));
        assertTrue(repo.findById("msg-1").isPresent());
    }

    @Test
    void save_updates_existing_message() {
        repo.save(message("msg-1", "group-1", "user-1", Instant.now()));

        MessageMetadata edited = new MessageMetadata("msg-1", "mongo-new", "group-1",
                "user-1", Instant.now(), Instant.now(), false, null, null);
        repo.save(edited);

        MessageMetadata found = repo.findById("msg-1").orElseThrow();
        assertEquals("mongo-new", found.mongoMessageId());
        assertNotNull(found.editedAt());
    }

    // ── markDeleted ───────────────────────────────────────────────────────────

    @Test
    void markDeleted_sets_is_deleted_and_deleted_at() {
        repo.save(message("msg-1", "group-1", "user-1", Instant.now()));
        repo.markDeleted("msg-1");

        MessageMetadata found = repo.findById("msg-1").orElseThrow();
        assertTrue(found.isDeleted());
        assertNotNull(found.deletedAt());
    }

    @Test
    void markDeleted_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.markDeleted("inexistant"));
    }
}