package tech.nabor.app.db.repository.incidents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IncidentRepositoryTest extends BaseRepositoryTest {

    private AppIncidentRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppIncidentRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
        userRepo.save(UserFixtures.userWithRole("mod-1", "mod@test.com", UserRole.moderator));
    }

    private Incident incident(String id, String reporterId, IncidentSeverity severity,
                              IncidentStatus status, String neighbourhoodId) {
        return new Incident(id, reporterId, null, neighbourhoodId, null,
                "Incident test", "Description", severity, status,
                null, Instant.now(), null, null);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_incident_when_exists() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.medium,
                IncidentStatus.open, "n-1"));

        Optional<Incident> found = repo.findById("inc-1");
        assertTrue(found.isPresent());
        assertEquals("inc-1", found.get().id());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    // ── findByReporterId ──────────────────────────────────────────────────────

    @Test
    void findByReporterId_returns_incidents_for_reporter() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.low,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-2", "user-1", IncidentSeverity.high,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-3", "user-2", IncidentSeverity.medium,
                IncidentStatus.open, "n-1"));

        assertEquals(2, repo.findByReporterId("user-1").size());
    }

    @Test
    void findByReporterId_returns_empty_when_no_incidents() {
        assertTrue(repo.findByReporterId("user-1").isEmpty());
    }

    // ── findByAssignedTo ──────────────────────────────────────────────────────

    @Test
    void findByAssignedTo_returns_assigned_incidents() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.medium,
                IncidentStatus.open, "n-1"));
        repo.assign("inc-1", "mod-1");

        List<Incident> assigned = repo.findByAssignedTo("mod-1");
        assertEquals(1, assigned.size());
        assertEquals("inc-1", assigned.get(0).id());
    }

    @Test
    void findByAssignedTo_returns_empty_when_none_assigned() {
        assertTrue(repo.findByAssignedTo("mod-1").isEmpty());
    }

    // ── findByNeighbourhood ───────────────────────────────────────────────────

    @Test
    void findByNeighbourhood_returns_incidents_for_neighbourhood() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.low,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-2", "user-1", IncidentSeverity.high,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-3", "user-1", IncidentSeverity.medium,
                IncidentStatus.open, "n-2"));

        assertEquals(2, repo.findByNeighbourhood("n-1", 10).size());
    }

    @Test
    void findByNeighbourhood_respects_limit() {
        for (int i = 1; i <= 5; i++) {
            repo.save(incident("inc-" + i, "user-1", IncidentSeverity.low,
                    IncidentStatus.open, "n-1"));
        }
        assertEquals(3, repo.findByNeighbourhood("n-1", 3).size());
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Test
    void findByStatus_returns_matching_incidents() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.low,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-2", "user-1", IncidentSeverity.high,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-3", "user-1", IncidentSeverity.medium,
                IncidentStatus.resolved, "n-1"));

        assertEquals(2, repo.findByStatus(IncidentStatus.open, 10).size());
    }

    // ── findBySeverity ────────────────────────────────────────────────────────

    @Test
    void findBySeverity_returns_matching_incidents() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.critical,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-2", "user-1", IncidentSeverity.critical,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-3", "user-1", IncidentSeverity.low,
                IncidentStatus.open, "n-1"));

        assertEquals(2, repo.findBySeverity(IncidentSeverity.critical, 10).size());
    }

    // ── findOpen ──────────────────────────────────────────────────────────────

    @Test
    void findOpen_returns_open_and_in_progress() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.low,
                IncidentStatus.open, "n-1"));
        repo.save(incident("inc-2", "user-1", IncidentSeverity.medium,
                IncidentStatus.in_progress, "n-1"));
        repo.save(incident("inc-3", "user-1", IncidentSeverity.high,
                IncidentStatus.resolved, "n-1"));

        assertEquals(2, repo.findOpen("n-1", 10).size());
    }

    @Test
    void findOpen_does_not_return_resolved() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.low,
                IncidentStatus.resolved, "n-1"));

        assertTrue(repo.findOpen("n-1", 10).isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_incident() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.medium,
                IncidentStatus.open, "n-1"));
        assertTrue(repo.findById("inc-1").isPresent());
    }

    @Test
    void save_updates_existing_incident() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.low,
                IncidentStatus.open, "n-1"));

        Incident updated = new Incident("inc-1", "user-1", null, "n-1", null,
                "Titre modifié", "Nouvelle description",
                IncidentSeverity.critical, IncidentStatus.in_progress,
                null, Instant.now(), Instant.now(), null);
        repo.save(updated);

        Incident found = repo.findById("inc-1").orElseThrow();
        assertEquals("Titre modifié",          found.title());
        assertEquals(IncidentSeverity.critical, found.severity());
        assertEquals(IncidentStatus.in_progress,found.status());
    }

    @Test
    void save_persists_all_severities() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.low,      IncidentStatus.open, "n-1"));
        repo.save(incident("inc-2", "user-1", IncidentSeverity.medium,   IncidentStatus.open, "n-1"));
        repo.save(incident("inc-3", "user-1", IncidentSeverity.high,     IncidentStatus.open, "n-1"));
        repo.save(incident("inc-4", "user-1", IncidentSeverity.critical, IncidentStatus.open, "n-1"));

        assertEquals(IncidentSeverity.low,      repo.findById("inc-1").orElseThrow().severity());
        assertEquals(IncidentSeverity.medium,   repo.findById("inc-2").orElseThrow().severity());
        assertEquals(IncidentSeverity.high,     repo.findById("inc-3").orElseThrow().severity());
        assertEquals(IncidentSeverity.critical, repo.findById("inc-4").orElseThrow().severity());
    }

    // ── assign ────────────────────────────────────────────────────────────────

    @Test
    void assign_sets_assigned_to_and_assigned_at() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.medium,
                IncidentStatus.open, "n-1"));
        repo.assign("inc-1", "mod-1");

        Incident found = repo.findById("inc-1").orElseThrow();
        assertEquals("mod-1",                   found.assignedTo());
        assertNotNull(found.assignedAt());
        assertEquals(IncidentStatus.in_progress, found.status());
    }

    @Test
    void assign_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.assign("inexistant", "mod-1"));
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_sets_status_and_resolved_at() {
        repo.save(incident("inc-1", "user-1", IncidentSeverity.medium,
                IncidentStatus.open, "n-1"));
        repo.resolve("inc-1");

        Incident found = repo.findById("inc-1").orElseThrow();
        assertEquals(IncidentStatus.resolved, found.status());
        assertNotNull(found.resolvedAt());
    }

    @Test
    void resolve_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.resolve("inexistant"));
    }
}