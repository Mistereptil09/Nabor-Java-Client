package tech.nabor.api.model.db_model.social;


import tech.nabor.api.model.db_model.enums.SwipeDirection;

import java.time.Instant;

public record UserSwipe(
        String swiperId,
        String swipedId,
        SwipeDirection direction,
        Instant swipedAt
) {}