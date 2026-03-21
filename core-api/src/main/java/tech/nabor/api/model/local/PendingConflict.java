package tech.nabor.api.model.local;

import java.time.Instant;

public record PendingConflict(
        int id,
        String tableName,
        String rowId,
        String fieldName,
        String localValue,
        String remoteValue,
        Instant detectedAt
) {}