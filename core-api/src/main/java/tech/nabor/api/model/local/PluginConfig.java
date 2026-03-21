package tech.nabor.api.model.local;

import java.time.Instant;

// Clés/valeurs arbitraires par plugin — extensible sans modifier le schéma
public record PluginConfig(
        String userId,
        String pluginId,
        String key,         // ex: "theme", "refresh_interval"
        String value,       // toujours String — le plugin parse ce dont il a besoin
        Instant updatedAt
) {}