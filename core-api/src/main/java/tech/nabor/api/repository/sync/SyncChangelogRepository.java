package tech.nabor.api.repository.sync;

import tech.nabor.api.event.ChangeEvent;
import tech.nabor.api.model.sync.SyncChange;

import java.util.List;

public interface SyncChangelogRepository {
    List<SyncChange> findAll();                         // all entries (no synced_at column anymore)
    List<SyncChange> findByTable(String tableName);
    java.util.Optional<SyncChange> findById(int id);
    void track(ChangeEvent event);                      // insert entry (outbox)
    void deleteByTableAndRow(String tableName, String rowId); // push success — remove from outbox
    void deleteAll();                                   // discard all pending (rollback before pull)
    void deleteById(int id);                            // rollback single entry
}