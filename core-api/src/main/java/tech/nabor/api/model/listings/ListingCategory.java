package tech.nabor.api.model.listings;

import java.time.Instant;

public record ListingCategory(
        int id,
        Integer parentCategory,   // nullable — Integer et pas int
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {}