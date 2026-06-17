package tech.nabor.api.repository.sync;

import tech.nabor.api.model.sync.SyncState;

import java.time.Instant;
import java.util.Optional;

public interface SyncStateRepository {
    Optional<SyncState> get();
    void save(SyncState state);
    void updateLatestCursor(String cursor);             // after a complete sync
    void updateResumeCursor(String cursor);             // after each page (crash recovery)
    void setRollingBack(boolean rollingBack);
}