package tech.nabor.api.model.local;

import java.time.Instant;

public record AppLocaleConfig(
        String userId,      // lié à l'utilisateur connecté
        String locale,      // "fr", "en", "es" — override du locale PostgreSQL
        Instant updatedAt
) {}