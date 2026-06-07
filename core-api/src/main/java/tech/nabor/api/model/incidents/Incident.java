package tech.nabor.api.model.incidents;

import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;

import java.time.Instant;

public record Incident(
        String id,
        String reporterId,
        String assignedTo,
        String neighbourhoodId,
        String mongoDocumentId,
        String title,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        Instant assignedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        // sync tracking (client-side only, not sent to server)
        String baseUpdatedAt,   // server updatedAt from last snapshot → sent as base_updated_at
        Instant syncedAt,       // when this entity was last successfully pushed
        boolean isDirty         // true = modified offline, needs push next cycle
) {}