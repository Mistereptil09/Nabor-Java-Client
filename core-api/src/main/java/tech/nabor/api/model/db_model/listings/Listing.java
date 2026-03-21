package tech.nabor.api.model.db_model.listings;

import tech.nabor.api.model.db_model.enums.ListingStatus;
import tech.nabor.api.model.db_model.enums.ListingType;

import java.time.Instant;

public record Listing(
        String id,
        String creatorId,
        String title,
        String description,
        Integer categoryId,
        ListingType listingType,
        int priceCents,
        ListingStatus status,
        String neighbourhoodId,
        String mongoDocumentId,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        Instant deletedAt
) {}
