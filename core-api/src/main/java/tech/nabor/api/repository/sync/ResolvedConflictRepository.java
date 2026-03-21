package tech.nabor.api.repository.sync;

import tech.nabor.api.model.sync.ResolvedConflict;

import java.util.List;
import java.util.Optional;

public interface ResolvedConflictRepository {
    // searches if a conflict was solved to not prompt about it again
    Optional<ResolvedConflict> findPrevious(String tableName, String rowId, String fieldName);
    List<ResolvedConflict> findByRow(String tableName, String rowId);
    void save(ResolvedConflict conflict);
    void deleteByRow(String tableName, String rowId);   // nettoyage après synchro réussie
}