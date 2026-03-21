package tech.nabor.api.model.db_model.events;

import java.time.Instant;

public record EventReport(
        String id,
        String eventId,
        String reporterId,
        String reason,
        Instant createdAt,
        Instant resolvedAt
) {}