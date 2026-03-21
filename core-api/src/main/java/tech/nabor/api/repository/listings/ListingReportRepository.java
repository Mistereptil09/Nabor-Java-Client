package tech.nabor.api.repository.listings;

import tech.nabor.api.model.listings.ListingReport;

import java.util.List;

public interface ListingReportRepository {
    List<ListingReport> findByListingId(String listingId);
    List<ListingReport> findUnresolved(int limit);       // resolved_at IS NULL
    void save(ListingReport report);
    void resolve(String id);                             // changes resolved_at
}