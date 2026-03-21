package tech.nabor.api.model.db_model.events;

import java.time.Instant;

public record EvenementCategory(
        int id,
        Integer parentCategory,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {}