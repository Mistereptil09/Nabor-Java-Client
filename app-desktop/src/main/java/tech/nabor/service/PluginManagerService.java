package tech.nabor.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import tech.nabor.api.ConnectedUser;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.model.local.PluginState;
import tech.nabor.app.PluginRegistry;


public class PluginManagerService {

    public record PluginView(String id, String displayName, boolean enabled, boolean hasUi) {}

    private final SqliteRepository db;
    private final PluginRegistry registry;
    private final String userId;

    public PluginManagerService(PluginContext ctx, PluginRegistry registry) {
        this.db = ctx.getDb();
        this.registry = registry;
        ConnectedUser user = ctx.getConnectedUser();
        this.userId = user.getUserId();
    }

    public List<PluginView> list() {
        List<PluginView> views = new ArrayList<>();
        for (NaborPlugin plugin : registry.getPlugins()) {
            boolean enabled = isEnabled(plugin.getId());
            views.add(new PluginView(
                    plugin.getId(), plugin.getDisplayName(), enabled, plugin.getView().isPresent()));
        }
        return views;
    }

    public boolean isEnabled(String pluginId) {
        return db.pluginStates().findByUserAndPlugin(userId, pluginId)
                .map(PluginState::enabled)
                .orElse(true); 
    }

    public void setEnabled(String pluginId, boolean enabled) {
        Optional<PluginState> existing = db.pluginStates().findByUserAndPlugin(userId, pluginId);
        int order = existing.map(PluginState::displayOrder).orElse(0);
        db.pluginStates().save(new PluginState(userId, pluginId, enabled, order, Instant.now()));
    }

    public void uninstall(String pluginId) {
        db.pluginConfigs().deleteAllForPlugin(userId, pluginId);
        db.pluginStates().delete(userId, pluginId);
    }
}
