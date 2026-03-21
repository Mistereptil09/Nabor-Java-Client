package tech.nabor.app;

import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class PluginRegistry {
    private final List<NaborPlugin> plugins = new ArrayList<>();

    public void loadAll(PluginContext ctx) {
        ServiceLoader<NaborPlugin> loader = ServiceLoader.load(NaborPlugin.class);
        for (NaborPlugin plugin : loader) {
            plugin.getView().ifPresent(view -> {
                // ajoute à la navigation seulement si un view existe
                // Todo add a navigation scrollbar on the ui
                // navigation.addTab(plugin.getDisplayName(), view);
            });
        }
    }
}