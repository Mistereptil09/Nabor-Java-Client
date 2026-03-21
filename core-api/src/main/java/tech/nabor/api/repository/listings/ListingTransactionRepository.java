package tech.nabor.api.repository.listings;

import tech.nabor.api.model.enums.TransactionStatus;
import tech.nabor.api.model.listings.ListingTransaction;

import java.util.List;
import java.util.Optional;

public interface ListingTransactionRepository {
    Optional<ListingTransaction> findById(String id);
    List<ListingTransaction> findByListingId(String listingId);
    List<ListingTransaction> findByProviderId(String providerId);
    List<ListingTransaction> findByRequesterId(String requesterId);
    List<ListingTransaction> findByStatus(TransactionStatus status, int limit);
    void save(ListingTransaction transaction);
}