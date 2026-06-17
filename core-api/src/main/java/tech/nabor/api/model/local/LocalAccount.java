package tech.nabor.api.model.local;

import java.time.Instant;

// Représente un compte connu sur cette installation
public record LocalAccount(
        String userId,
        String email,
        String displayName,
        boolean isActive,       // true = currently active account
        Instant lastLoginAt,
        String refreshToken     // encrypted, for auto-login between reboots
) {
    /** Backwards-compatible constructor without refresh token. */
    public LocalAccount(String userId, String email, String displayName,
                        boolean isActive, Instant lastLoginAt) {
        this(userId, email, displayName, isActive, lastLoginAt, null);
    }
}