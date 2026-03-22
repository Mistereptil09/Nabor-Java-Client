package tech.nabor.app.db.repository.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.events.EventModerationAction;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.api.model.enums.ModerationAction;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EventModerationActionRepositoryTest extends BaseRepositoryTest {

    private AppEventModerationActionRepository repo;
    private AppUserRepository userRepo;
    private AppEvenementRepository evenementRepo;

    @BeforeEach
    void setUp() {
        repo          = new AppEventModerationActionRepository(jdbi);
        userRepo      = new AppUserRepository(jdbi);
        evenementRepo = new AppEvenementRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.userWithRole("mod-1", "mod@test.com", UserRole.moderator));
        userRepo.save(UserFixtures.userWithRole("mod-2", "mod2@test.com", UserRole.moderator));

        evenementRepo.save(new Evenement("event-1", "user-1", "n-1", null, null,
                "Event 1", EventStatus.open, null, 0, null, null,
                null, 48, null, null, null, null, Instant.now(), null, null));
        evenementRepo.save(new Evenement("event-2", "user-1", "n-1", null, null,
                "Event 2", EventStatus.open, null, 0, null, null,
                null, 48, null, null, null, null, Instant.now(), null, null));
    }

    private EventModerationAction action(String id, String eventId,
                                         String moderatorId, ModerationAction action) {
        return new EventModerationAction(id, eventId, moderatorId,
                action, "Raison de test", Instant.now());
    }

    // ── findByEventId ─────────────────────────────────────────────────────────

    @Test
    void findByEventId_returns_actions_for_event() {
        repo.save(action("action-1", "event-1", "mod-1", ModerationAction.warned));
        repo.save(action("action-2", "event-1", "mod-1", ModerationAction.cancelled));
        repo.save(action("action-3", "event-2", "mod-1", ModerationAction.warned));

        assertEquals(2, repo.findByEventId("event-1").size());
    }

    @Test
    void findByEventId_returns_empty_when_no_actions() {
        assertTrue(repo.findByEventId("event-1").isEmpty());
    }

    // ── findByModeratorId ─────────────────────────────────────────────────────

    @Test
    void findByModeratorId_returns_actions_for_moderator() {
        repo.save(action("action-1", "event-1", "mod-1", ModerationAction.warned));
        repo.save(action("action-2", "event-2", "mod-1", ModerationAction.cancelled));
        repo.save(action("action-3", "event-1", "mod-2", ModerationAction.warned));

        assertEquals(2, repo.findByModeratorId("mod-1").size());
    }

    @Test
    void findByModeratorId_returns_empty_when_no_actions() {
        assertTrue(repo.findByModeratorId("mod-1").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_action() {
        repo.save(action("action-1", "event-1", "mod-1", ModerationAction.warned));
        assertEquals(1, repo.findByEventId("event-1").size());
    }

    @Test
    void save_persists_all_moderation_actions() {
        repo.save(action("action-1", "event-1", "mod-1", ModerationAction.warned));
        repo.save(action("action-2", "event-2", "mod-1", ModerationAction.cancelled));
        repo.save(action("action-3", "event-1", "mod-2", ModerationAction.restored));

        assertEquals(ModerationAction.warned,
                repo.findByEventId("event-1").stream()
                        .filter(a -> a.id().equals("action-1"))
                        .findFirst().orElseThrow().action());
        assertEquals(ModerationAction.cancelled,
                repo.findByEventId("event-2").get(0).action());
        assertEquals(ModerationAction.restored,
                repo.findByEventId("event-1").stream()
                        .filter(a -> a.id().equals("action-3"))
                        .findFirst().orElseThrow().action());
    }

    @Test
    void save_persists_reason() {
        repo.save(new EventModerationAction("action-1", "event-1", "mod-1",
                ModerationAction.warned, "Contenu offensant", Instant.now()));

        EventModerationAction found = repo.findByEventId("event-1").get(0);
        assertEquals("Contenu offensant", found.reason());
    }
}