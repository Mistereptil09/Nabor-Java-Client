package tech.nabor.app.db.repository.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.api.model.local.PluginState;
import tech.nabor.app.db.BaseRepositoryTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginStateRepositoryTest extends BaseRepositoryTest {

    private AppPluginStateRepository repo;
    private AppLocalAccountRepository accountRepo;

    @BeforeEach
    void setUp() {
        repo        = new AppPluginStateRepository(jdbi);
        accountRepo = new AppLocalAccountRepository(jdbi);

        accountRepo.save(new LocalAccount("user-1", "a@test.com", "User A", false, null));
        accountRepo.save(new LocalAccount("user-2", "b@test.com", "User B", false, null));
    }

    // ── findByUserId ──────────────────────────────────────────────────────────

    @Test
    void findByUserId_returns_empty_when_no_plugins() {
        assertTrue(repo.findByUserId("user-1").isEmpty());
    }

    @Test
    void findByUserId_returns_all_plugins_for_user() {
        repo.save(new PluginState("user-1", "plugin-social",    true,  0, null));
        repo.save(new PluginState("user-1", "plugin-messaging", false, 1, null));
        repo.save(new PluginState("user-2", "plugin-social",    true,  0, null));

        List<PluginState> result = repo.findByUserId("user-1");
        assertEquals(2, result.size());
    }

    @Test
    void findByUserId_does_not_return_other_users_plugins() {
        repo.save(new PluginState("user-2", "plugin-social", true, 0, null));

        assertTrue(repo.findByUserId("user-1").isEmpty());
    }

    // ── findByUserAndPlugin ───────────────────────────────────────────────────

    @Test
    void findByUserAndPlugin_returns_correct_plugin() {
        repo.save(new PluginState("user-1", "plugin-social", true, 0, null));

        java.util.Optional<PluginState> found = repo.findByUserAndPlugin("user-1", "plugin-social");
        assertTrue(found.isPresent());
        assertEquals("plugin-social", found.get().pluginId());
        assertTrue(found.get().enabled());
    }

    @Test
    void findByUserAndPlugin_returns_empty_when_not_found() {
        assertTrue(repo.findByUserAndPlugin("user-1", "plugin-social").isEmpty());
    }

    // ── findEnabledByUserId ───────────────────────────────────────────────────

    @Test
    void findEnabledByUserId_returns_only_enabled_plugins() {
        repo.save(new PluginState("user-1", "plugin-social",    true,  0, null));
        repo.save(new PluginState("user-1", "plugin-messaging", false, 1, null));

        List<PluginState> enabled = repo.findEnabledByUserId("user-1");
        assertEquals(1, enabled.size());
        assertEquals("plugin-social", enabled.getFirst().pluginId());
    }

    @Test
    void findEnabledByUserId_ordered_by_display_order() {
        repo.save(new PluginState("user-1", "plugin-social",    true, 2, null));
        repo.save(new PluginState("user-1", "plugin-messaging", true, 0, null));
        repo.save(new PluginState("user-1", "plugin-sync",      true, 1, null));

        List<PluginState> enabled = repo.findEnabledByUserId("user-1");
        assertEquals("plugin-messaging", enabled.get(0).pluginId());
        assertEquals("plugin-sync",      enabled.get(1).pluginId());
        assertEquals("plugin-social",    enabled.get(2).pluginId());
    }

    @Test
    void findEnabledByUserId_returns_empty_when_all_disabled() {
        repo.save(new PluginState("user-1", "plugin-social", false, 0, null));

        assertTrue(repo.findEnabledByUserId("user-1").isEmpty());
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_inserts_new_plugin_state() {
        repo.save(new PluginState("user-1", "plugin-social", true, 0, null));

        assertEquals(1, repo.findByUserId("user-1").size());
    }

    @Test
    void save_updates_existing_plugin_state() {
        repo.save(new PluginState("user-1", "plugin-social", true,  0, null));
        repo.save(new PluginState("user-1", "plugin-social", false, 1, null));

        PluginState found = repo.findByUserAndPlugin("user-1", "plugin-social").orElseThrow();
        assertFalse(found.enabled());
        assertEquals(1, found.displayOrder());
        assertEquals(1, repo.findByUserId("user-1").size()); // pas de doublon
    }

    @Test
    void save_persists_updated_at() {
        Instant now = Instant.now();
        repo.save(new PluginState("user-1", "plugin-social", true, 0, now));

        PluginState found = repo.findByUserAndPlugin("user-1", "plugin-social").orElseThrow();
        assertEquals(now.toEpochMilli(), found.updatedAt().toEpochMilli());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removes_plugin_state() {
        repo.save(new PluginState("user-1", "plugin-social", true, 0, null));
        repo.delete("user-1", "plugin-social");

        assertTrue(repo.findByUserAndPlugin("user-1", "plugin-social").isEmpty());
    }

    @Test
    void delete_only_removes_target_plugin() {
        repo.save(new PluginState("user-1", "plugin-social",    true, 0, null));
        repo.save(new PluginState("user-1", "plugin-messaging", true, 1, null));

        repo.delete("user-1", "plugin-social");

        assertEquals(1, repo.findByUserId("user-1").size());
        assertTrue(repo.findByUserAndPlugin("user-1", "plugin-messaging").isPresent());
    }

    @Test
    void delete_nonexistent_plugin_does_not_throw() {
        assertDoesNotThrow(() -> repo.delete("user-1", "inexistant"));
    }
}