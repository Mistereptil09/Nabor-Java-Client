package tech.nabor.api.model.sync;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Response from {@code GET /sync/snapshot?since=&limit=&cursor=}.
 * Per CDC §4.10: the server returns entity arrays using camelCase field names.
 * The cursor is base64(timestamp) — decoded by the client for pagination.
 */
public record SyncSnapshotResponse(
        String sync_at,              // server timestamp — becomes new last_sync_at
        boolean has_more,            // true = more pages, continue with cursor
        String cursor,               // base64(timestamp), null when has_more = false
        JsonNode incidents,
        JsonNode listing_moderation_actions,
        JsonNode event_moderation_actions,
        JsonNode listing_reports,
        JsonNode event_reports,
        JsonNode users_raw,
        JsonNode listings,
        JsonNode events,
        JsonNode chat_groups,
        JsonNode votes,
        JsonNode polls,
        JsonNode listing_transactions,
        JsonNode listing_categories,
        JsonNode event_categories,
        JsonNode poll_options,
        JsonNode event_participants,
        JsonNode users_in_group,
        JsonNode follows,
        JsonNode friendships
) {}
