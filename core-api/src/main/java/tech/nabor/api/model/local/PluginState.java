package tech.nabor.api.model.local;

import java.time.Instant;

// Une ligne par plugin par utilisateur
public record PluginState(
        String userId,
        String pluginId,    // "plugin-social", "plugin-messaging"
        boolean enabled,
        int displayOrder,   // position dans la nav, 0 = premier
        Instant updatedAt
) {}