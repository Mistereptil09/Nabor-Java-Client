package tech.nabor.app.db.repository.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.events.EventReport;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventReportRepositoryTest extends BaseRepositoryTest {

    private AppEventReportRepository repo;
    private AppUserRepository userRepo;
    private AppEvenementRepository evenementRepo;

    @BeforeEach
    void setUp() {
        repo          = new AppEventReportRepository(jdbi);
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

    private EventReport report(String id, String eventId) {
        return new EventReport(id, eventId, "user-2", "Contenu inapproprié",
                Instant.now(), null);
    }

    // ── findByEventId ─────────────────────────────────────────────────────────

    @Test
    void findByEventId_returns_reports_for_event() {
        repo.save(report("report-1", "event-1"));
        repo.save(report("report-2", "event-1"));
        repo.save(report("report-3", "event-2"));

        assertEquals(2, repo.findByEventId("event-1").size());
    }

    @Test
    void findByEventId_returns_empty_when_no_reports() {
        assertTrue(repo.findByEventId("event-1").isEmpty());
    }

    // ── findUnresolved ────────────────────────────────────────────────────────

    @Test
    void findUnresolved_returns_only_unresolved() {
        repo.save(report("report-1", "event-1"));
        repo.save(report("report-2", "event-1"));
        repo.resolve("report-1");

        List<EventReport> unresolved = repo.findUnresolved(10);
        assertEquals(1, unresolved.size());
        assertEquals("report-2", unresolved.getFirst().id());
    }

    @Test
    void findUnresolved_respects_limit() {
        repo.save(report("report-1", "event-1"));
        repo.save(report("report-2", "event-1"));
        repo.save(report("report-3", "event-2"));

        assertEquals(2, repo.findUnresolved(2).size());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_report() {
        repo.save(report("report-1", "event-1"));
        assertEquals(1, repo.findByEventId("event-1").size());
    }

    @Test
    void save_persists_all_fields() {
        Instant now = Instant.now();
        repo.save(new EventReport("report-1", "event-1", "user-2", "Spam", now, null));

        EventReport found = repo.findByEventId("event-1").getFirst();
        assertEquals("report-1", found.id());
        assertEquals("user-2",   found.reporterId());
        assertEquals("Spam",     found.reason());
        assertNull(found.resolvedAt());
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_sets_resolved_at() {
        repo.save(report("report-1", "event-1"));
        repo.resolve("report-1");

        assertNotNull(repo.findByEventId("event-1").getFirst().resolvedAt());
    }

    @Test
    void resolve_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.resolve("inexistant"));
    }
}