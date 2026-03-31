package tech.nabor.app;

import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.local.PluginState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public class PluginRegistry {

    private final List<NaborPlugin> allPlugins = new ArrayList<>();
    private final PluginContext ctx;

    // ids of the core plugins that shouldn't be allowed to deactivate
    private static final List<String> CORE_PLUGIN_IDS = List.of("plugin-sync");

    public PluginRegistry(PluginContext ctx) {
        this.ctx = ctx;
    }

    // ── Loading ────────────────────────────────────────────────────────────

    public void loadAll() {
        ServiceLoader<NaborPlugin> loader = ServiceLoader.load(NaborPlugin.class);

        for (NaborPlugin plugin : loader) {
            // register the plugin traductions
            ctx.getI18n().registerBundle(plugin.getId(), plugin.getClass().getClassLoader());

            // initialise le plugin avec le contexte
            // initialize the plugin with context
            plugin.initialize(ctx);
            allPlugins.add(plugin);

            // create a new default PluginState if the plugin was never seen before
            String userId = ctx.getConnectedUser().getUserId();
            Optional<PluginState> existing = ctx.getDb().pluginStates()
                    .findByUserAndPlugin(userId, plugin.getId());

            if (existing.isEmpty()) {
                ctx.getDb().pluginStates().save(new PluginState(
                        userId,
                        plugin.getId(),
                        true,                    // default true
                        allPlugins.size() - 1,   // insertion order
                        java.time.Instant.now()
                ));
            }
        }
    }

    // ── Plugin access ─────────────────────────────────────────────────────

    // plugins UI activés, triés par displayOrder
    // UI plugin activated, sorted by displayOrder
    public List<NaborPlugin> getUiPlugins() {
        String userId = ctx.getConnectedUser().getUserId();
        List<PluginState> enabledStates = ctx.getDb().pluginStates()
                .findEnabledByUserId(userId);

        return enabledStates.stream()
                .map(state -> findById(state.pluginId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(plugin -> plugin.getView().isPresent()) // UI only
                .toList();
    }

    // every plugin is loaded, UI or background
    public List<NaborPlugin> getAll() {
        return List.copyOf(allPlugins);
    }

    public Optional<NaborPlugin> findById(String pluginId) {
        return allPlugins.stream()
                .filter(p -> p.getId().equals(pluginId))
                .findFirst();
    }

    // ── Activation / Deactivation ────────────────────────────────────────────

    public void enable(String pluginId) {
        if (isCorePlugin(pluginId)) return; // Core plugin cannot be modified
        updateState(pluginId, true);
    }

    public void disable(String pluginId) {
        if (isCorePlugin(pluginId)) return;
        updateState(pluginId, false);
    }

    public boolean isEnabled(String pluginId) {
        if (isCorePlugin(pluginId)) return true;
        String userId = ctx.getConnectedUser().getUserId();
        return ctx.getDb().pluginStates()
                .findByUserAndPlugin(userId, pluginId)
                .map(PluginState::enabled)
                .orElse(false);
    }

    public boolean isCorePlugin(String pluginId) {
        return CORE_PLUGIN_IDS.contains(pluginId);
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    public void shutdownAll() {
        for (NaborPlugin plugin : allPlugins) {
            try {
                plugin.shutdown();
            } catch (Exception e) {
                // un plugin qui plante au shutdown ne bloque pas les autres
                // A plugin that fails on shutdown will not block the others
                System.err.println("PluginRegistry: erreur shutdown " +
                        plugin.getId() + " : " + e.getMessage());
            }
        }
        allPlugins.clear();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void updateState(String pluginId, boolean enabled) {
        String userId = ctx.getConnectedUser().getUserId();
        ctx.getDb().pluginStates()
                .findByUserAndPlugin(userId, pluginId)
                .ifPresent(state -> ctx.getDb().pluginStates().save(new PluginState(
                        state.userId(),
                        state.pluginId(),
                        enabled,
                        state.displayOrder(),
                        java.time.Instant.now()
                )));
    }
}