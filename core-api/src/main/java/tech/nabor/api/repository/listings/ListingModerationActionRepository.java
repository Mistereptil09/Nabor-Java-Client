package tech.nabor.api.repository.listings;

import tech.nabor.api.model.listings.ListingModerationAction;

import java.util.List;

public interface ListingModerationActionRepository {
    List<ListingModerationAction> findByListingId(String listingId);
    List<ListingModerationAction> findByModeratorId(String moderatorId);
    void save(ListingModerationAction action);
}