package tech.nabor.api.model.local;

import java.time.Instant;

// Représente un compte connu sur cette installation
public record LocalAccount(
        String userId,
        String email,           // affiché dans le sélecteur de compte
        String displayName,     // prénom + nom mis en cache localement
        boolean isActive,       // true = compte actuellement connecté
        Instant lastLoginAt
) {}