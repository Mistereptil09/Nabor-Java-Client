package tech.nabor.app.db.repository.listings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.listings.Listing;
import tech.nabor.api.model.listings.ListingTransaction;
import tech.nabor.api.model.enums.ListingStatus;
import tech.nabor.api.model.enums.ListingType;
import tech.nabor.api.model.enums.TransactionStatus;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.fixtures.UserFixtures;
import tech.nabor.app.db.repository.user.AppUserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ListingTransactionRepositoryTest extends BaseRepositoryTest {

    private AppListingTransactionRepository repo;
    private AppUserRepository userRepo;
    private AppListingRepository listingRepo;

    @BeforeEach
    void setUp() {
        repo        = new AppListingTransactionRepository(jdbi);
        userRepo    = new AppUserRepository(jdbi);
        listingRepo = new AppListingRepository(jdbi);

        userRepo.save(UserFixtures.basicUser("user-1", "a@test.com"));
        userRepo.save(UserFixtures.basicUser("user-2", "b@test.com"));

        listingRepo.save(new Listing("listing-1", "user-1", "T", null,
                null, ListingType.offer, 1000, ListingStatus.open,
                null, null, Instant.now(), null, null, null));
    }

    private ListingTransaction transaction(String id, TransactionStatus status) {
        return new ListingTransaction(id, "listing-1", "user-1", "user-2",
                1000, 100, null, null, null, null, null,
                status, Instant.now(), null, null, null);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_transaction_when_exists() {
        repo.save(transaction("tx-1", TransactionStatus.pending));

        Optional<ListingTransaction> found = repo.findById("tx-1");
        assertTrue(found.isPresent());
        assertEquals("tx-1", found.get().id());
    }

    @Test
    void findById_returns_empty_when_not_found() {
        assertTrue(repo.findById("inexistant").isEmpty());
    }

    // ── findByListingId ───────────────────────────────────────────────────────

    @Test
    void findByListingId_returns_transactions_for_listing() {
        repo.save(transaction("tx-1", TransactionStatus.pending));
        repo.save(transaction("tx-2", TransactionStatus.completed));

        assertEquals(2, repo.findByListingId("listing-1").size());
    }

    @Test
    void findByListingId_returns_empty_when_no_transactions() {
        assertTrue(repo.findByListingId("listing-1").isEmpty());
    }

    // ── findByProviderId ──────────────────────────────────────────────────────

    @Test
    void findByProviderId_returns_transactions_for_provider() {
        repo.save(transaction("tx-1", TransactionStatus.pending));
        repo.save(transaction("tx-2", TransactionStatus.completed));

        assertEquals(2, repo.findByProviderId("user-1").size());
    }

    @Test
    void findByProviderId_does_not_return_requester_transactions() {
        repo.save(transaction("tx-1", TransactionStatus.pending));

        assertTrue(repo.findByProviderId("user-2").isEmpty());
    }

    // ── findByRequesterId ─────────────────────────────────────────────────────

    @Test
    void findByRequesterId_returns_transactions_for_requester() {
        repo.save(transaction("tx-1", TransactionStatus.pending));

        assertEquals(1, repo.findByRequesterId("user-2").size());
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Test
    void findByStatus_returns_matching_transactions() {
        repo.save(transaction("tx-1", TransactionStatus.pending));
        repo.save(transaction("tx-2", TransactionStatus.pending));
        repo.save(transaction("tx-3", TransactionStatus.completed));

        assertEquals(2, repo.findByStatus(TransactionStatus.pending, 10).size());
    }

    @Test
    void findByStatus_respects_limit() {
        repo.save(transaction("tx-1", TransactionStatus.pending));
        repo.save(transaction("tx-2", TransactionStatus.pending));
        repo.save(transaction("tx-3", TransactionStatus.pending));

        assertEquals(2, repo.findByStatus(TransactionStatus.pending, 2).size());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_transaction() {
        repo.save(transaction("tx-1", TransactionStatus.pending));
        assertTrue(repo.findById("tx-1").isPresent());
    }

    @Test
    void save_updates_existing_transaction() {
        repo.save(transaction("tx-1", TransactionStatus.pending));

        ListingTransaction updated = new ListingTransaction(
                "tx-1", "listing-1", "user-1", "user-2",
                1000, 100, null, null, null, null, null,
                TransactionStatus.completed, Instant.now(), Instant.now(), Instant.now(), null);
        repo.save(updated);

        ListingTransaction found = repo.findById("tx-1").orElseThrow();
        assertEquals(TransactionStatus.completed, found.status());
        assertNotNull(found.completedAt());
    }

    @Test
    void save_persists_all_statuses() {
        repo.save(transaction("tx-1", TransactionStatus.pending));
        repo.save(transaction("tx-2", TransactionStatus.completed));
        repo.save(transaction("tx-3", TransactionStatus.payment_failed));
        repo.save(transaction("tx-4", TransactionStatus.cancelled));

        assertEquals(TransactionStatus.pending,        repo.findById("tx-1").orElseThrow().status());
        assertEquals(TransactionStatus.completed,      repo.findById("tx-2").orElseThrow().status());
        assertEquals(TransactionStatus.payment_failed, repo.findById("tx-3").orElseThrow().status());
        assertEquals(TransactionStatus.cancelled,      repo.findById("tx-4").orElseThrow().status());
    }
}