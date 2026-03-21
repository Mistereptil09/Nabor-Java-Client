package tech.nabor.api.model.social;


import tech.nabor.api.model.enums.SwipeDirection;

import java.time.Instant;

public record UserSwipe(
        String swiperId,
        String swipedId,
        SwipeDirection direction,
        Instant swipedAt
) {}