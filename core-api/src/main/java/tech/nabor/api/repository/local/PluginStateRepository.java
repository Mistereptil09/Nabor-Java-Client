package tech.nabor.api.repository.local;

import tech.nabor.api.model.local.PluginState;

import java.util.List;
import java.util.Optional;

public interface PluginStateRepository {
    List<PluginState> findByUserId(String userId);              // all user  plugins
    Optional<PluginState> findByUserAndPlugin(String userId, String pluginId);
    List<PluginState> findEnabledByUserId(String userId);       // active plugins only, sorted by displayOrder
    void save(PluginState state);
    void delete(String userId, String pluginId);
}