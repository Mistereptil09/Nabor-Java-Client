package tech.nabor.api.model.listings;

import tech.nabor.api.model.enums.ModerationAction;

import java.time.Instant;

public record ListingModerationAction(
        String id,
        String listingId,
        String moderatorId,
        ModerationAction action,
        String reason,
        Instant createdAt
) {}