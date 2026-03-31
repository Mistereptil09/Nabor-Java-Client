// core-impl/src/test/java/tech/nabor/app/PluginRegistryTest.java
package tech.nabor.app;

import javafx.scene.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.app.db.BaseRepositoryTest;
import tech.nabor.app.db.DatabaseManager;

import java.io.File;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PluginRegistryTest extends BaseRepositoryTest {

    private PluginRegistry registry;
    private AppPluginContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        // BaseRepositoryTest provides jdbi via a temp file
        AppSqliteRepository db = new AppSqliteRepository(
                new DatabaseManager(tempDbPath(), new TestI18n()));

        db.localAccounts().save(
                new LocalAccount("user-1", "a@test.com", "Antonio", true, null));

        AppConnectedUser user = new AppConnectedUser("user-1", "a@test.com", "admin");
        AppEventBus eventBus  = new AppEventBus(new TestI18n());
        AppI18n i18n          = new AppI18n("fr");
        AppNaborReporter err  = new AppNaborReporter();
        AppNaborHttpClient http = new AppNaborHttpClient("http://localhost:3000", "token");

        ctx      = new AppPluginContext(db, http, user, eventBus, err, i18n);
        registry = new PluginRegistry(ctx);
    }

    // test UI plugin
    private NaborPlugin uiPlugin(String id) {
        return new NaborPlugin() {
            private boolean initialized = false;
            private boolean shutdown    = false;

            @Override public String getId()          { return id; }
            @Override public String getDisplayName() { return id; }
            @Override public void initialize(PluginContext ctx) { initialized = true; }
            @Override public Optional<Node> getView() { return Optional.of(new javafx.scene.layout.Pane()); }
            @Override public void shutdown()          { shutdown = true; }
            public boolean wasInitialized() { return initialized; }
            public boolean wasShutdown()    { return shutdown; }
        };
    }

    // test background plugin
    private NaborPlugin backgroundPlugin(String id) {
        return new NaborPlugin() {
            @Override public String getId()          { return id; }
            @Override public String getDisplayName() { return id; }
            @Override public void initialize(PluginContext ctx) {}
            @Override public Optional<Node> getView() { return Optional.empty(); }
            @Override public void shutdown() {}
        };
    }

    // helper — loads plugins directly without ServiceLoader
    private void loadPlugin(NaborPlugin plugin) {
        plugin.initialize(ctx);
        registry.getAll(); // forces access to the internal list
        // we use reflection to add directly — necessary because loadAll() uses ServiceLoader
        try {
            var field = PluginRegistry.class.getDeclaredField("allPlugins");
            field.setAccessible(true);
            ((java.util.List<NaborPlugin>) field.get(registry)).add(plugin);

            // creates the PluginState
            ctx.getDb().pluginStates().save(new tech.nabor.api.model.local.PluginState(
                    "user-1", plugin.getId(), true, 0, java.time.Instant.now()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returns_plugin_when_loaded() {
        NaborPlugin plugin = uiPlugin("plugin-social");
        loadPlugin(plugin);

        assertTrue(registry.findById("plugin-social").isPresent());
    }

    @Test
    void findById_returns_empty_when_not_loaded() {
        assertTrue(registry.findById("inexistant").isEmpty());
    }

    // ── getUiPlugins ──────────────────────────────────────────────────────────

    @Test
    void getUiPlugins_returns_only_ui_plugins() {
        loadPlugin(uiPlugin("plugin-social"));
        loadPlugin(backgroundPlugin("plugin-sync"));

        assertEquals(1, registry.getUiPlugins().size());
        assertEquals("plugin-social", registry.getUiPlugins().get(0).getId());
    }

    @Test
    void getUiPlugins_does_not_return_disabled_plugins() {
        loadPlugin(uiPlugin("plugin-social"));
        registry.disable("plugin-social");

        assertTrue(registry.getUiPlugins().isEmpty());
    }

    // ── enable / disable ──────────────────────────────────────────────────────

    @Test
    void enable_activates_disabled_plugin() {
        loadPlugin(uiPlugin("plugin-social"));
        registry.disable("plugin-social");
        registry.enable("plugin-social");

        assertTrue(registry.isEnabled("plugin-social"));
    }

    @Test
    void disable_deactivates_plugin() {
        loadPlugin(uiPlugin("plugin-social"));
        registry.disable("plugin-social");

        assertFalse(registry.isEnabled("plugin-social"));
    }

    // ── core plugins ─────────────────────────────────────────────────────────

    @Test
    void core_plugin_cannot_be_disabled() {
        loadPlugin(backgroundPlugin("plugin-sync"));
        registry.disable("plugin-sync");

        assertTrue(registry.isEnabled("plugin-sync"));
    }

    @Test
    void isCorePlugin_returns_true_for_sync() {
        assertTrue(registry.isCorePlugin("plugin-sync"));
    }

    @Test
    void isCorePlugin_returns_false_for_optional_plugins() {
        assertFalse(registry.isCorePlugin("plugin-social"));
    }

    // ── shutdownAll ───────────────────────────────────────────────────────────

    @Test
    void shutdownAll_clears_all_plugins() {
        loadPlugin(uiPlugin("plugin-social"));
        loadPlugin(backgroundPlugin("plugin-sync"));

        registry.shutdownAll();

        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    void shutdownAll_failing_plugin_does_not_block_others() {
        NaborPlugin badPlugin = new NaborPlugin() {
            @Override public String getId()          { return "plugin-bad"; }
            @Override public String getDisplayName() { return "Bad"; }
            @Override public void initialize(PluginContext ctx) {}
            @Override public Optional<Node> getView() { return Optional.empty(); }
            @Override public void shutdown() { throw new RuntimeException("Shutdown failed"); }
        };

        NaborPlugin goodPlugin = uiPlugin("plugin-social");
        loadPlugin(badPlugin);
        loadPlugin(goodPlugin);

        assertDoesNotThrow(() -> registry.shutdownAll());
    }

    // helper
    private String tempDbPath() throws Exception {
        File f = File.createTempFile("nabor_registry_test_", ".db");
        f.deleteOnExit();
        return f.getAbsolutePath();
    }
}
