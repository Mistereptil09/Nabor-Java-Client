package tech.nabor.app;

import tech.nabor.api.EventBus;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class PluginRegistry {
    private final List<NaborPlugin> plugins = new ArrayList<>();
    private EventBus eventBus;

    /** Event published when the plugin list changes (load, unload, enable, disable). */
    public static final String PLUGINS_CHANGED = "plugins.changed";

    public List<NaborPlugin> getPlugins() {
        return new ArrayList<>(plugins);
    }

    /**
     * Loads plugins via ServiceLoader (dev classpath) and from {@code plugins/*.jar}
     * (production). Duplicate plugins (same ID) are skipped.
     */
    public void loadAll(PluginContext ctx) {
        this.eventBus = ctx.getEventBus();

        // Dev: classpath-based loading via ServiceLoader
        ServiceLoader<NaborPlugin> loader = ServiceLoader.load(NaborPlugin.class);
        for (NaborPlugin plugin : loader) {
            register(ctx, plugin);
        }

        // Production: load from plugins/*.jar via URLClassLoader
        loadFromJars(ctx);

        if (eventBus != null) {
            eventBus.publish(PLUGINS_CHANGED, null);
        }
    }

    private void loadFromJars(PluginContext ctx) {
        File installDir;
        try {
            installDir = new File(PluginRegistry.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            installDir = new File(".");
        }

        File dir = new File(installDir, "plugins/");
        if (!dir.exists() || !dir.isDirectory()) {
            dir = new File("plugins/");
        }
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) return;

        for (File jar : jars) {
            try {
                URLClassLoader jarLoader = new URLClassLoader(
                        new URL[]{jar.toURI().toURL()},
                        getClass().getClassLoader());
                ServiceLoader<NaborPlugin> jarServiceLoader =
                        ServiceLoader.load(NaborPlugin.class, jarLoader);
                for (NaborPlugin plugin : jarServiceLoader) {
                    register(ctx, plugin);
                }
            } catch (Exception e) {
                System.err.println("[PluginRegistry] Failed to load jar: "
                        + jar.getName() + " — " + e.getMessage());
            }
        }
    }

    private void register(PluginContext ctx, NaborPlugin plugin) {
        if (plugins.stream().anyMatch(p -> p.getId().equals(plugin.getId()))) {
            System.out.println("[PluginRegistry] Skipping duplicate: " + plugin.getId());
            return;
        }
        System.out.println("[PluginRegistry] Loading plugin: "
                + plugin.getDisplayName() + " (" + plugin.getId() + ")");
        plugin.initialize(ctx);
        plugins.add(plugin);
    }

    /** Unloads a plugin by ID: calls shutdown() and removes it. */
    public boolean unload(String pluginId) {
        var it = plugins.iterator();
        while (it.hasNext()) {
            NaborPlugin p = it.next();
            if (p.getId().equals(pluginId)) {
                try { p.shutdown(); } catch (Exception ignored) {}
                it.remove();
                System.out.println("[PluginRegistry] Unloaded: " + pluginId);
                if (eventBus != null) eventBus.publish(PLUGINS_CHANGED, pluginId);
                return true;
            }
        }
        return false;
    }

    public void shutdownAll() {
        System.out.println("[PluginRegistry] Shutting down " + plugins.size() + " plugin(s)...");
        for (NaborPlugin plugin : plugins) {
            try { plugin.shutdown(); }
            catch (Exception e) {
                System.err.println("[PluginRegistry] Error shutting down "
                        + plugin.getId() + ": " + e.getMessage());
            }
        }
        plugins.clear();
    }
}
