package tech.nabor.api.model.local;

import java.time.Instant;

public record SyncState(
        Instant lastSyncedAt,
        String lastSyncToken
) {}