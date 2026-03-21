package tech.nabor.api.repository.sync;

import tech.nabor.api.event.ChangeEvent;
import tech.nabor.api.model.sync.SyncChange;

import java.util.List;

public interface SyncChangelogRepository {
    List<SyncChange> findUnsynced();                    // synced_at IS NULL
    List<SyncChange> findByTable(String tableName);
    void track(ChangeEvent event);                      // inserts an entry
    void markSynced(int id);                            // changes synced_at = now
    void markAllSynced();                               // successful push — mark all
    void deleteUnsynced();                              // rollback — delete non-pushed changes
}