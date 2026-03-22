package tech.nabor.app.db.repository.messages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.messages.ChatGroup;
import tech.nabor.api.model.messages.MessageMetadata;
import tech.nabor.api.model.messages.MessageReadReceipt;
import tech.nabor.api.model.enums.ChatGroupType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageReadReceiptRepositoryTest extends BaseRepositoryTest {

    private AppMessageReadReceiptRepository repo;
    private AppUserRepository userRepo;
    private AppChatGroupRepository groupRepo;
    private AppMessageMetadataRepository messageRepo;

    @BeforeEach
    void setUp() {
        repo        = new AppMessageReadReceiptRepository(jdbi);
        userRepo    = new AppUserRepository(jdbi);
        groupRepo   = new AppChatGroupRepository(jdbi);
        messageRepo = new AppMessageMetadataRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.basicUser("user-3", "c@test.com"));

        groupRepo.save(new ChatGroup("group-1", "G1", null, "user-1",
                ChatGroupType.group_chat, null, Instant.now(), null, null));

        messageRepo.save(new MessageMetadata("msg-1", "mongo-1", "group-1",
                "user-1", Instant.now(), null, false, null, null));
        messageRepo.save(new MessageMetadata("msg-2", "mongo-2", "group-1",
                "user-1", Instant.now(), null, false, null, null));
    }

    private MessageReadReceipt receipt(String messageId, String userId) {
        return new MessageReadReceipt(messageId, userId, Instant.now());
    }

    // ── findByMessageId ───────────────────────────────────────────────────────

    @Test
    void findByMessageId_returns_receipts_for_message() {
        repo.save(receipt("msg-1", "user-1"));
        repo.save(receipt("msg-1", "user-2"));
        repo.save(receipt("msg-2", "user-1"));

        List<MessageReadReceipt> result = repo.findByMessageId("msg-1");
        assertEquals(2, result.size());
    }

    @Test
    void findByMessageId_returns_empty_when_no_receipts() {
        assertTrue(repo.findByMessageId("msg-1").isEmpty());
    }

    @Test
    void findByMessageId_does_not_return_other_messages() {
        repo.save(receipt("msg-2", "user-1"));
        assertTrue(repo.findByMessageId("msg-1").isEmpty());
    }

    // ── hasRead ───────────────────────────────────────────────────────────────

    @Test
    void hasRead_returns_true_when_read() {
        repo.save(receipt("msg-1", "user-1"));
        assertTrue(repo.hasRead("user-1", "msg-1"));
    }

    @Test
    void hasRead_returns_false_when_not_read() {
        assertFalse(repo.hasRead("user-1", "msg-1"));
    }

    @Test
    void hasRead_is_not_symmetric() {
        repo.save(receipt("msg-1", "user-1"));
        assertFalse(repo.hasRead("user-2", "msg-1"));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_receipt() {
        repo.save(receipt("msg-1", "user-1"));
        assertTrue(repo.hasRead("user-1", "msg-1"));
    }

    @Test
    void save_updates_existing_receipt() {
        repo.save(receipt("msg-1", "user-1"));
        Instant later = Instant.now().plusSeconds(10);
        repo.save(new MessageReadReceipt("msg-1", "user-1", later));

        List<MessageReadReceipt> receipts = repo.findByMessageId("msg-1");
        assertEquals(1, receipts.size()); // pas de doublon
        assertEquals(later.toEpochMilli(), receipts.get(0).readAt().toEpochMilli());
    }

    @Test
    void save_persists_read_at() {
        Instant now = Instant.now();
        repo.save(new MessageReadReceipt("msg-1", "user-1", now));

        MessageReadReceipt found = repo.findByMessageId("msg-1").get(0);
        assertEquals(now.toEpochMilli(), found.readAt().toEpochMilli());
    }

    @Test
    void save_multiple_users_can_read_same_message() {
        repo.save(receipt("msg-1", "user-1"));
        repo.save(receipt("msg-1", "user-2"));
        repo.save(receipt("msg-1", "user-3"));

        assertEquals(3, repo.findByMessageId("msg-1").size());
    }
}