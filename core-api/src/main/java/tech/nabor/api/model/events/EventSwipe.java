package tech.nabor.api.model.events;

import tech.nabor.api.model.enums.SwipeDirection;

import java.time.Instant;

public record EventSwipe(
        String userId,
        String eventId,
        SwipeDirection direction,
        Instant swipedAt
) {}
