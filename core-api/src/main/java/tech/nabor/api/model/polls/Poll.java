package tech.nabor.api.model.polls;

import tech.nabor.api.model.enums.PollType;

import java.time.Instant;

public record Poll(
        String id,
        String title,
        String description,
        String creatorId,
        String neighbourhoodId,
        PollType pollType,
        Instant startsAt,
        Instant endsAt,
        boolean isAnonymous,
        Instant closedAt,
        String closedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {}