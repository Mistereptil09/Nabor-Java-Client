package tech.nabor.api.model.local;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SyncChange(
        int id,
        String tableName,
        String rowId,
        String operation,
        List<String> changedFields,
        Map<String, String> previousValues, // null for INSERT
        Map<String, String> newValues,      // null for DELETE
        Instant changedAt,
        Instant syncedAt
) {}