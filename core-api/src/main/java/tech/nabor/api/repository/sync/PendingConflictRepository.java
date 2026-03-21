package tech.nabor.api.repository.sync;

import tech.nabor.api.model.sync.PendingConflict;

import java.util.List;

public interface PendingConflictRepository {
    List<PendingConflict> findAll();                    // all waiting conflicts
    List<PendingConflict> findByTable(String tableName);
    List<PendingConflict> findByRow(String tableName, String rowId);
    boolean hasConflicts();                             // verify before push
    void save(PendingConflict conflict);
    void delete(int id);                                // resolved conflict
    void deleteAll();                                   // all solved — push possible
}