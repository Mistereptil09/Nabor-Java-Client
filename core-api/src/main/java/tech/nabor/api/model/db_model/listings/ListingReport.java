package tech.nabor.api.model.db_model.listings;

import java.time.Instant;

public record ListingReport(
        String id,
        String listingId,
        String reporterId,
        String reason,
        Instant createdAt,
        Instant resolvedAt
) {}
