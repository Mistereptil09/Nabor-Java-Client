package tech.nabor.api.model.db_model.events;

import tech.nabor.api.model.db_model.enums.SwipeDirection;

import java.time.Instant;

public record EventSwipe(
        String userId,
        String eventId,
        SwipeDirection direction,
        Instant swipedAt
) {}
