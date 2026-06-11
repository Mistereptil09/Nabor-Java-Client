package tech.nabor.api.repository.sync;

import tech.nabor.api.model.sync.SyncWhitelist;

import java.util.List;

public interface SyncWhitelistRepository {
    List<SyncWhitelist> findByType(String entityType);
    List<SyncWhitelist> findAll();
    void replaceAll(String entityType, List<String> fields);
}