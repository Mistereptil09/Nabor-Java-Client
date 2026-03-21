package tech.nabor.api.model.db_model.events;

import tech.nabor.api.model.db_model.enums.EventStatus;

import java.time.Instant;

public record Evenement(
        String id,
        String creatorId,
        String neighbourhoodId,
        Integer categoryId,
        String groupId,
        String title,
        EventStatus status,
        String inviteCode,
        int costCents,
        Instant startsAt,
        Instant endsAt,
        Integer maxParticipants,
        int refundDeadlineHours,
        String mongoDocumentId,
        Instant publishedAt,
        Instant cancelledAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {}
