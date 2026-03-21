package tech.nabor.api.model.local;

import java.time.Instant;

public record ResolvedConflict(
        int id,
        String tableName,
        String rowId,
        String fieldName,
        String chosenValue,      // "local" or "remote"
        Instant resolvedAt
) {}