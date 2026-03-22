package tech.nabor.app.db.repository.listings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.listings.Listing;
import tech.nabor.api.model.listings.ListingReport;
import tech.nabor.api.model.enums.ListingStatus;
import tech.nabor.api.model.enums.ListingType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListingReportRepositoryTest extends BaseRepositoryTest {

    private AppListingReportRepository repo;
    private AppUserRepository userRepo;
    private AppListingRepository listingRepo;

    @BeforeEach
    void setUp() {
        repo        = new AppListingReportRepository(jdbi);
        userRepo    = new AppUserRepository(jdbi);
        listingRepo = new AppListingRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));

        listingRepo.save(new Listing("listing-1", "user-1", "T", null,
                null, ListingType.offer, 0, ListingStatus.open,
                null, null, Instant.now(), null, null, null));
        listingRepo.save(new Listing("listing-2", "user-1", "T", null,
                null, ListingType.offer, 0, ListingStatus.open,
                null, null, Instant.now(), null, null, null));
    }

    private ListingReport report(String id, String listingId) {
        return new ListingReport(id, listingId, "user-2", "Contenu inapproprié",
                Instant.now(), null);
    }

    // ── findByListingId ───────────────────────────────────────────────────────

    @Test
    void findByListingId_returns_reports_for_listing() {
        repo.save(report("report-1", "listing-1"));
        repo.save(report("report-2", "listing-1"));
        repo.save(report("report-3", "listing-2"));

        assertEquals(2, repo.findByListingId("listing-1").size());
    }

    @Test
    void findByListingId_returns_empty_when_no_reports() {
        assertTrue(repo.findByListingId("listing-1").isEmpty());
    }

    // ── findUnresolved ────────────────────────────────────────────────────────

    @Test
    void findUnresolved_returns_only_unresolved() {
        repo.save(report("report-1", "listing-1"));
        repo.save(report("report-2", "listing-1"));
        repo.resolve("report-1");

        List<ListingReport> unresolved = repo.findUnresolved(10);
        assertEquals(1, unresolved.size());
        assertEquals("report-2", unresolved.get(0).id());
    }

    @Test
    void findUnresolved_respects_limit() {
        repo.save(report("report-1", "listing-1"));
        repo.save(report("report-2", "listing-1"));
        repo.save(report("report-3", "listing-2"));

        assertEquals(2, repo.findUnresolved(2).size());
    }

    @Test
    void findUnresolved_returns_empty_when_all_resolved() {
        repo.save(report("report-1", "listing-1"));
        repo.resolve("report-1");

        assertTrue(repo.findUnresolved(10).isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_report() {
        repo.save(report("report-1", "listing-1"));
        assertEquals(1, repo.findByListingId("listing-1").size());
    }

    @Test
    void save_persists_all_fields() {
        Instant now = Instant.now();
        repo.save(new ListingReport("report-1", "listing-1", "user-2",
                "Spam", now, null));

        ListingReport found = repo.findByListingId("listing-1").get(0);
        assertEquals("report-1",  found.id());
        assertEquals("user-2",    found.reporterId());
        assertEquals("Spam",      found.reason());
        assertNull(found.resolvedAt());
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_sets_resolved_at() {
        repo.save(report("report-1", "listing-1"));
        repo.resolve("report-1");

        ListingReport found = repo.findByListingId("listing-1").get(0);
        assertNotNull(found.resolvedAt());
    }

    @Test
    void resolve_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.resolve("inexistant"));
    }
}