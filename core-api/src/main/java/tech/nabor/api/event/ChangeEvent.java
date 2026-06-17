package tech.nabor.api.event;

import java.time.Instant;
import java.util.Map;

public record ChangeEvent(
        String tableName,
        String rowId,
        String operation,                     // "INSERT", "UPDATE", "DELETE"
        Map<String, String> previousValues,   // null for INSERT
        Map<String, String> newValues,        // null for DELETE
        String baseUpdatedAt,                 // server updatedAt before edit; null for INSERT
        Instant occurredAt
) {}