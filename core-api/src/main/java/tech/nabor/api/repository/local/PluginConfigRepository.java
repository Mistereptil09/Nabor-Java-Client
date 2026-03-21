package tech.nabor.api.repository.local;

import java.util.Map;
import java.util.Optional;

public interface PluginConfigRepository {
    Optional<String> getValue(String userId, String pluginId, String key);
    Map<String, String> getAllForPlugin(String userId, String pluginId); // all keys of a single plugin
    void setValue(String userId, String pluginId, String key, String value);
    void deleteKey(String userId, String pluginId, String key);
    void deleteAllForPlugin(String userId, String pluginId);    // removes all data from a said plugin
}