package tech.nabor.api.model.sync;

import java.util.List;
import java.util.Map;

/**
 * Response from {@code POST /sync/updates}.
 * Per CDC §4.10: success=false means at least one conflict, but
 * non-conflicting updates in the same batch are still applied.
 */
public record SyncPushResponse(
        boolean success,           // false if at least one conflict
        boolean has_conflicts,
        int applied_count,
        int conflict_count,
        List<SyncPushResult> results
) {
    public record SyncPushResult(
            String entity_type,
            String entity_id,
            String status,               // "applied" | "conflict" | "skipped"
            SyncPushConflict conflict    // present only when status = "conflict"
    ) {}

    public record SyncPushConflict(
            String field_name,           // null = whole-record conflict
            Map<String, Object> client_data,  // what the client sent in "changes"
            Map<String, Object> server_data   // current server-side entity state
    ) {}
}
