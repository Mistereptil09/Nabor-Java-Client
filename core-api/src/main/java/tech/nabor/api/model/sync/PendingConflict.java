package tech.nabor.api.model.sync;

import java.time.Instant;

public record PendingConflict(
        int id,
        String tableName,
        String rowId,
        String fieldName,       // null = whole-record conflict (multiple fields differ)
        String localValue,      // JSON: client_data from the push conflict response
        String remoteValue,     // JSON: server_data from the push conflict response
        Instant detectedAt
) {}