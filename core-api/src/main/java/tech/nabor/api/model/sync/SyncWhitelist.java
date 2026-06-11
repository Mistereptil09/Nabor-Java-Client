package tech.nabor.api.model.sync;

public record SyncWhitelist(
        String entityType,
        String fieldName
) {}