package tech.nabor.app.db.repository.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.events.EventParticipant;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.api.model.enums.ParticipantStatus;
import tech.nabor.api.model.enums.PaymentStatus;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EventParticipantRepositoryTest extends BaseRepositoryTest {

    private AppEventParticipantRepository repo;
    private AppUserRepository userRepo;
    private AppEvenementRepository evenementRepo;

    @BeforeEach
    void setUp() {
        repo          = new AppEventParticipantRepository(jdbi);
        userRepo      = new AppUserRepository(jdbi);
        evenementRepo = new AppEvenementRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.basicUser("user-3", "c@test.com"));

        evenementRepo.save(new Evenement("event-1", "user-1", "n-1", null, null,
                "Event 1", EventStatus.open, null, 0, null, null,
                10, 48, null, null, null, null,
                Instant.now(), null, null));
        evenementRepo.save(new Evenement("event-2", "user-1", "n-1", null, null,
                "Event 2", EventStatus.open, null, 0, null, null,
                null, 48, null, null, null, null,
                Instant.now(), null, null));
    }

    private EventParticipant participant(String userId, String eventId,
                                         ParticipantStatus status) {
        return new EventParticipant(userId, eventId, status, PaymentStatus.free,
                null, null, 0, Instant.now(), null, null, null, null, null, null);
    }

    // ── findByUserAndEvent ────────────────────────────────────────────────────

    @Test
    void findByUserAndEvent_returns_participant_when_exists() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));

        Optional<EventParticipant> found = repo.findByUserAndEvent("user-1", "event-1");
        assertTrue(found.isPresent());
        assertEquals(ParticipantStatus.registered, found.get().status());
    }

    @Test
    void findByUserAndEvent_returns_empty_when_not_found() {
        assertTrue(repo.findByUserAndEvent("user-1", "event-1").isEmpty());
    }

    // ── findByEventId ─────────────────────────────────────────────────────────

    @Test
    void findByEventId_returns_all_participants() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));
        repo.save(participant("user-2", "event-1", ParticipantStatus.waitlisted));
        repo.save(participant("user-3", "event-2", ParticipantStatus.registered));

        assertEquals(2, repo.findByEventId("event-1").size());
    }

    @Test
    void findByEventId_returns_empty_when_no_participants() {
        assertTrue(repo.findByEventId("event-1").isEmpty());
    }

    // ── findByEventAndStatus ──────────────────────────────────────────────────

    @Test
    void findByEventAndStatus_returns_matching_participants() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));
        repo.save(participant("user-2", "event-1", ParticipantStatus.waitlisted));

        List<EventParticipant> registered =
                repo.findByEventAndStatus("event-1", ParticipantStatus.registered);
        assertEquals(1, registered.size());
        assertEquals("user-1", registered.getFirst().userId());
    }

    @Test
    void findByEventAndStatus_ordered_by_registered_at_asc() {
        repo.save(new EventParticipant("user-2", "event-1", ParticipantStatus.waitlisted,
                PaymentStatus.free, null, null, 0,
                Instant.now().plusSeconds(10), null, null, null, null, null, null));
        repo.save(new EventParticipant("user-1", "event-1", ParticipantStatus.waitlisted,
                PaymentStatus.free, null, null, 0,
                Instant.now(), null, null, null, null, null, null));

        List<EventParticipant> waitlisted =
                repo.findByEventAndStatus("event-1", ParticipantStatus.waitlisted);
        assertEquals("user-1", waitlisted.getFirst().userId()); // inscrit en premier
    }

    // ── findByUserId ──────────────────────────────────────────────────────────

    @Test
    void findByUserId_returns_all_participations() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));
        repo.save(participant("user-1", "event-2", ParticipantStatus.waitlisted));

        assertEquals(2, repo.findByUserId("user-1").size());
    }

    // ── countRegistered ───────────────────────────────────────────────────────

    @Test
    void countRegistered_counts_only_registered() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));
        repo.save(participant("user-2", "event-1", ParticipantStatus.registered));
        repo.save(participant("user-3", "event-1", ParticipantStatus.waitlisted));

        assertEquals(2, repo.countRegistered("event-1"));
    }

    @Test
    void countRegistered_returns_zero_when_no_participants() {
        assertEquals(0, repo.countRegistered("event-1"));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_participant() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));
        assertTrue(repo.findByUserAndEvent("user-1", "event-1").isPresent());
    }

    @Test
    void save_updates_existing_participant() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.waitlisted));
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));

        EventParticipant found = repo.findByUserAndEvent("user-1", "event-1").orElseThrow();
        assertEquals(ParticipantStatus.registered, found.status());
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_sets_status_and_cancelled_at() {
        repo.save(participant("user-1", "event-1", ParticipantStatus.registered));
        repo.cancel("user-1", "event-1");

        EventParticipant found = repo.findByUserAndEvent("user-1", "event-1").orElseThrow();
        assertEquals(ParticipantStatus.cancelled, found.status());
        assertNotNull(found.cancelledAt());
    }

    @Test
    void cancel_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.cancel("user-1", "event-1"));
    }
}