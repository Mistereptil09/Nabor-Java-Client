package tech.nabor.api.model.db_model.social;

import java.time.Instant;

public record UserBlock(
        String blockerId,
        String blockedId,
        Instant blockedAt
) {}
