package tech.nabor.app;

import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class PluginRegistry {
    private final List<NaborPlugin> plugins = new ArrayList<>();

    public List<NaborPlugin> getPlugins() {
        return new ArrayList<>(plugins);
    }

    public void loadAll(PluginContext ctx) {
        ServiceLoader<NaborPlugin> loader = ServiceLoader.load(NaborPlugin.class);
        for (NaborPlugin plugin : loader) {
            System.out.println("[PluginRegistry] Loading plugin: " + plugin.getDisplayName() + " (" + plugin.getId() + ")");
            
            // Initialize the plugin
            plugin.initialize(ctx);
            plugins.add(plugin);
            
            plugin.getView().ifPresent(view -> {
                // ajoute à la navigation seulement si un view existe
                // Todo add a navigation scrollbar on the ui
                // navigation.addTab(plugin.getDisplayName(), view);
            });
        }
    }

    public void shutdownAll() {
        System.out.println("[PluginRegistry] Shutting down " + plugins.size() + " plugin(s)...");
        for (NaborPlugin plugin : plugins) {
            try {
                plugin.shutdown();
            } catch (Exception e) {
                System.err.println("[PluginRegistry] Error shutting down plugin " + plugin.getId() + ": " + e.getMessage());
            }
        }
        plugins.clear();
    }
}