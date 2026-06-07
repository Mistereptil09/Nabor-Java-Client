package tech.nabor.api.model.sync;

import java.time.Instant;

public record ResolvedConflict(
        int id,
        String tableName,
        String rowId,
        String fieldName,        // null = whole-record conflict
        String chosenValue,      // "local" | "remote" | "auto"
        Instant resolvedAt
) {}