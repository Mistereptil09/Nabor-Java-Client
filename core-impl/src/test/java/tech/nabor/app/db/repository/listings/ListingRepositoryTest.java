package tech.nabor.app.db.repository.listings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.listings.Listing;
import tech.nabor.api.model.enums.ListingStatus;
import tech.nabor.api.model.enums.ListingType;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ListingRepositoryTest extends BaseRepositoryTest {

    private AppListingRepository repo;
    private AppUserRepository userRepo;

    @BeforeEach
    void setUp() {
        repo     = new AppListingRepository(jdbi);
        userRepo = new AppUserRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));
    }

    private Listing listing(String id, String creatorId, ListingStatus status) {
        return new Listing(id, creatorId, "Titre", "Description",
                null, ListingType.offer, 1000, status,
                "neighbourhood-1", null, Instant.now(), null, null, null);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_listing_when_exists() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));

        Optional<Listing> found = repo.findById("listing-1");
        assertTrue(found.isPresent());
        assertEquals("listing-1", found.get().id());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    @Test
    void findById_returns_soft_deleted_listing() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        repo.delete("listing-1");

        assertTrue(repo.findById("listing-1").isPresent());
        assertNotNull(repo.findById("listing-1").get().deletedAt());
    }

    // ── findByCreatorId ───────────────────────────────────────────────────────

    @Test
    void findByCreatorId_returns_listings_for_creator() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        repo.save(listing("listing-2", "user-1", ListingStatus.open));
        repo.save(listing("listing-3", "user-2", ListingStatus.open));

        assertEquals(2, repo.findByCreatorId("user-1").size());
    }

    @Test
    void findByCreatorId_does_not_return_soft_deleted() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        repo.delete("listing-1");

        assertTrue(repo.findByCreatorId("user-1").isEmpty());
    }

    @Test
    void findByCreatorId_returns_empty_when_no_listings() {
        assertTrue(repo.findByCreatorId("user-1").isEmpty());
    }

    // ── findByNeighbourhood ───────────────────────────────────────────────────

    @Test
    void findByNeighbourhood_returns_matching_listings() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        repo.save(listing("listing-2", "user-1", ListingStatus.open));
        repo.save(new Listing("listing-3", "user-1", "T", null,
                null, ListingType.offer, 0, ListingStatus.open,
                "other-neighbourhood", null, Instant.now(), null, null, null));

        assertEquals(2, repo.findByNeighbourhood("neighbourhood-1", ListingStatus.open, 10).size());
    }

    @Test
    void findByNeighbourhood_filters_by_status() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        repo.save(listing("listing-2", "user-1", ListingStatus.closed));

        assertEquals(1, repo.findByNeighbourhood("neighbourhood-1", ListingStatus.open, 10).size());
    }

    @Test
    void findByNeighbourhood_respects_limit() {
        for (int i = 1; i <= 5; i++) {
            repo.save(listing("listing-" + i, "user-1", ListingStatus.open));
        }
        assertEquals(3, repo.findByNeighbourhood("neighbourhood-1", ListingStatus.open, 3).size());
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Test
    void findByStatus_returns_matching_listings() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        repo.save(listing("listing-2", "user-1", ListingStatus.open));
        repo.save(listing("listing-3", "user-1", ListingStatus.closed));

        assertEquals(2, repo.findByStatus(ListingStatus.open, 10).size());
    }

    @Test
    void findByStatus_respects_limit() {
        for (int i = 1; i <= 5; i++) {
            repo.save(listing("listing-" + i, "user-1", ListingStatus.open));
        }
        assertEquals(2, repo.findByStatus(ListingStatus.open, 2).size());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_listing() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        assertTrue(repo.findById("listing-1").isPresent());
    }

    @Test
    void save_updates_existing_listing() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));

        Listing updated = new Listing("listing-1", "user-1", "Nouveau titre", null,
                null, ListingType.request, 2000, ListingStatus.pending,
                "neighbourhood-1", null, Instant.now(), Instant.now(), null, null);
        repo.save(updated);

        Listing found = repo.findById("listing-1").orElseThrow();
        assertEquals("Nouveau titre",      found.title());
        assertEquals(ListingType.request,  found.listingType());
        assertEquals(2000,                 found.priceCents());
        assertEquals(ListingStatus.pending,found.status());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_sets_deleted_at() {
        repo.save(listing("listing-1", "user-1", ListingStatus.open));
        repo.delete("listing-1");

        assertNotNull(repo.findById("listing-1").orElseThrow().deletedAt());
    }

    @Test
    void delete_nonexistent_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("inexistant"));
    }
}