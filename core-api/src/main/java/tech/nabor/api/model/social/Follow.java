package tech.nabor.api.model.social;

import java.time.Instant;

public record Follow(
        String followerId,
        String followedId,
        Instant followedAt
) {}
