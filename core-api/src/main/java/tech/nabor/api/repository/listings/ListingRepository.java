package tech.nabor.api.repository.listings;

import tech.nabor.api.model.enums.ListingStatus;
import tech.nabor.api.model.listings.Listing;

import java.util.List;
import java.util.Optional;

public interface ListingRepository {
    List<Listing> findAll();
    Optional<Listing> findById(String id);
    List<Listing> findByCreatorId(String creatorId);
    List<Listing> findByNeighbourhood(String neighbourhoodId, ListingStatus status, int limit);
    List<Listing> findByStatus(ListingStatus status, int limit);
    void save(Listing listing);
    void delete(String id);                              // soft delete — changes deleted_at
}