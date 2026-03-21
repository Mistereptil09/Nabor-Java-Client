package tech.nabor.api.model.sync;

import java.time.Instant;

public record SyncState(
        Instant lastSyncedAt,
        String lastSyncToken
) {}