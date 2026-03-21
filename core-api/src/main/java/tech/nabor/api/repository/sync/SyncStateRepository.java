package tech.nabor.api.repository.sync;

import tech.nabor.api.model.sync.SyncState;

import java.time.Instant;
import java.util.Optional;

public interface SyncStateRepository {
    Optional<SyncState> get();                          // one line — id = 1
    void save(SyncState state);                         // insert or update
    void updateLastSyncedAt(Instant syncedAt);          // after a successful push
    void updateSyncToken(String token);                 // token retuned by NestJS
    void setRollingBack(boolean rollingBack);           // flag rollback in action
}