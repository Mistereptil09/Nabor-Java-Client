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
        Instant resolvedAt
) {}