package tech.nabor.plugin.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.sync.SyncState;

/**
 * Handles pull (server → client): cursor-based paginated snapshot.
 * Updates sync_state (resume_cursor, latest_sync_cursor) for crash recovery.
 */
class PullEngine {

    private final PluginContext ctx;
    private final SnapshotProcessor processor;
    private final ObjectMapper mapper = new ObjectMapper();

    PullEngine(PluginContext ctx) {
        this.ctx = ctx;
        this.processor = new SnapshotProcessor(ctx);
    }

    /** Incremental pull using stored cursors from sync_state. */
    void pull() throws Exception {
        var state = ctx.getDb().syncState().get();
        String cursor = state.map(SyncState::resumeCursor)
                .filter(c -> c != null && !c.isBlank())
                .orElseGet(() -> state.map(SyncState::latestSyncCursor)
                        .filter(c -> c != null && !c.isBlank())
                        .orElse(null));
        pullLoop(cursor, null);
    }

    /** Full pull from epoch. */
    void pullFromEpoch() throws Exception {
        pullLoop(null, "1970-01-01T00:00:00.000Z");
    }

    /** Pull with a user-specified since timestamp. */
    void pullSince(String since) throws Exception {
        pullLoop(null, since);
    }

    // ── Core loop ──────────────────────────────────────────────────────────

    private void pullLoop(String startCursor, String since) throws Exception {
        String cursor = startCursor;
        boolean hasMore;

        String fallbackSince = (since != null && !since.isBlank())
                ? since : "1970-01-01T00:00:00.000Z";
        do {
            String url = "/sync/snapshot?limit=500&since=" + fallbackSince;
            if (cursor != null && !cursor.isBlank()) {
                url += "&cursor=" + cursor;
            }

            String json = ctx.getHttpClient().get(url);
            JsonNode root = mapper.readTree(json);

            // Clear conflicts on first successful page
            if (cursor == null || cursor.equals(startCursor)) {
                ctx.getDb().pendingConflicts().deleteAll();
            }

            processor.processAllEntities(root);

            cursor = root.path("cursor").asText(null);
            hasMore = root.path("has_more").asBoolean(false);

            // Persist resume cursor after each page (crash recovery)
            ctx.getDb().syncState().updateResumeCursor(cursor);

            System.out.println("[Sync] Pull page done — has_more=" + hasMore);

        } while (hasMore);

        // Sync complete — promote resume to latest
        if (cursor != null) {
            ctx.getDb().syncState().updateLatestCursor(cursor);
        }
    }
}
