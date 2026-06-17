package tech.nabor.api.model.sync;

import java.time.Instant;

public record SyncState(
        String latestSyncCursor,   // cursor from the last fully-completed sync
        String resumeCursor,       // cursor for crash recovery mid-pagination; null when sync complete
        boolean isRollingBack
) {}