package tech.nabor.api.model.db_model.user;

import java.time.Instant;

public record UserSession(
        String id,
        String userId,
        String refreshTokenHash,
        String deviceName,
        String ipAddress,
        String userAgent,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt
) {}