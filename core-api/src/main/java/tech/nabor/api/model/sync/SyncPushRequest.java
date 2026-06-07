package tech.nabor.api.model.sync;

import java.util.List;
import java.util.Map;

/**
 * Body for {@code POST /sync/updates} — batch of offline changes to push.
 * Per CDC §4.10: max 100 entities per request, idempotent via jobId.
 */
public record SyncPushRequest(
        String jobId,
        List<SyncPushUpdate> updates
) {
    public record SyncPushUpdate(
            String entity_type,
            String entity_id,
            Map<String, Object> changes,   // only the modified fields
            String base_updated_at          // server updatedAt from last snapshot, NOT local clock
    ) {}
}
