package tech.nabor.api.model.user;

import tech.nabor.api.model.enums.MessagePolicy;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.enums.Visibility;

import java.time.Instant;

public record User(
        String id,
        String firstName,
        String lastName,
        String email,
        String passwordHash,
        String totpSecret,
        String stripeAccountId,
        String neighbourhoodId,
        Visibility visibility,
        String bio,
        MessagePolicy messagePolicy,
        String locale,
        String profilePictureMongoId,
        String bannerMongoId,
        UserRole role,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {}