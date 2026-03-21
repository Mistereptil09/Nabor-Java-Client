package tech.nabor.app.db.repository.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.app.db.BaseRepositoryTest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigRepositoryTest extends BaseRepositoryTest {

    private AppPluginConfigRepository repo;
    private AppLocalAccountRepository accountRepo;

    @BeforeEach
    void setUp() {
        repo        = new AppPluginConfigRepository(jdbi);
        accountRepo = new AppLocalAccountRepository(jdbi);

        accountRepo.save(new LocalAccount("user-1", "a@test.com", "User A", false, null));
        accountRepo.save(new LocalAccount("user-2", "b@test.com", "User B", false, null));
    }

    // ── getValue ──────────────────────────────────────────────────────────────

    @Test
    void getValue_returns_value_when_exists() {
        repo.setValue("user-1", "plugin-social", "theme", "dark");

        Optional<String> value = repo.getValue("user-1", "plugin-social", "theme");
        assertTrue(value.isPresent());
        assertEquals("dark", value.get());
    }

    @Test
    void getValue_returns_empty_when_key_not_found() {
        assertTrue(repo.getValue("user-1", "plugin-social", "inexistant").isEmpty());
    }

    @Test
    void getValue_does_not_return_other_users_value() {
        repo.setValue("user-2", "plugin-social", "theme", "dark");

        assertTrue(repo.getValue("user-1", "plugin-social", "theme").isEmpty());
    }

    @Test
    void getValue_does_not_return_other_plugins_value() {
        repo.setValue("user-1", "plugin-messaging", "theme", "dark");

        assertTrue(repo.getValue("user-1", "plugin-social", "theme").isEmpty());
    }

    // ── getAllForPlugin ────────────────────────────────────────────────────────

    @Test
    void getAllForPlugin_returns_empty_map_when_no_config() {
        assertTrue(repo.getAllForPlugin("user-1", "plugin-social").isEmpty());
    }

    @Test
    void getAllForPlugin_returns_all_keys_for_plugin() {
        repo.setValue("user-1", "plugin-social", "theme",            "dark");
        repo.setValue("user-1", "plugin-social", "refresh_interval", "30");
        repo.setValue("user-1", "plugin-messaging", "theme",         "light"); // autre plugin

        Map<String, String> config = repo.getAllForPlugin("user-1", "plugin-social");
        assertEquals(2, config.size());
        assertEquals("dark", config.get("theme"));
        assertEquals("30",   config.get("refresh_interval"));
    }

    @Test
    void getAllForPlugin_does_not_return_other_users_config() {
        repo.setValue("user-2", "plugin-social", "theme", "dark");

        assertTrue(repo.getAllForPlugin("user-1", "plugin-social").isEmpty());
    }

    // ── setValue ──────────────────────────────────────────────────────────────

    @Test
    void setValue_inserts_new_key() {
        repo.setValue("user-1", "plugin-social", "theme", "dark");

        assertEquals("dark", repo.getValue("user-1", "plugin-social", "theme").orElseThrow());
    }

    @Test
    void setValue_updates_existing_key() {
        repo.setValue("user-1", "plugin-social", "theme", "dark");
        repo.setValue("user-1", "plugin-social", "theme", "light");

        assertEquals("light", repo.getValue("user-1", "plugin-social", "theme").orElseThrow());
        assertEquals(1, repo.getAllForPlugin("user-1", "plugin-social").size()); // pas de doublon
    }

    @Test
    void setValue_stores_null_value() {
        repo.setValue("user-1", "plugin-social", "theme", null);

        // The repository stores a row with a NULL value. Optional from getValue follows
        // java.util.Optional semantics (empty when underlying value is NULL), so we
        // assert presence of the key via getAllForPlugin and expect getValue() to be empty.
        Map<String, String> all = repo.getAllForPlugin("user-1", "plugin-social");
        assertTrue(all.containsKey("theme"));
        assertNull(all.get("theme"));

        Optional<String> value = repo.getValue("user-1", "plugin-social", "theme");
        assertTrue(value.isEmpty());
    }

    // ── deleteKey ─────────────────────────────────────────────────────────────

    @Test
    void deleteKey_removes_specific_key() {
        repo.setValue("user-1", "plugin-social", "theme",            "dark");
        repo.setValue("user-1", "plugin-social", "refresh_interval", "30");

        repo.deleteKey("user-1", "plugin-social", "theme");

        assertTrue(repo.getValue("user-1", "plugin-social", "theme").isEmpty());
        assertTrue(repo.getValue("user-1", "plugin-social", "refresh_interval").isPresent());
    }

    @Test
    void deleteKey_nonexistent_key_does_not_throw() {
        assertDoesNotThrow(() -> repo.deleteKey("user-1", "plugin-social", "inexistant"));
    }

    // ── deleteAllForPlugin ────────────────────────────────────────────────────

    @Test
    void deleteAllForPlugin_removes_all_keys_for_plugin() {
        repo.setValue("user-1", "plugin-social", "theme",            "dark");
        repo.setValue("user-1", "plugin-social", "refresh_interval", "30");

        repo.deleteAllForPlugin("user-1", "plugin-social");

        assertTrue(repo.getAllForPlugin("user-1", "plugin-social").isEmpty());
    }

    @Test
    void deleteAllForPlugin_does_not_affect_other_plugins() {
        repo.setValue("user-1", "plugin-social",    "theme", "dark");
        repo.setValue("user-1", "plugin-messaging", "theme", "light");

        repo.deleteAllForPlugin("user-1", "plugin-social");

        assertTrue(repo.getAllForPlugin("user-1", "plugin-social").isEmpty());
        assertEquals(1, repo.getAllForPlugin("user-1", "plugin-messaging").size());
    }

    @Test
    void deleteAllForPlugin_does_not_affect_other_users() {
        repo.setValue("user-1", "plugin-social", "theme", "dark");
        repo.setValue("user-2", "plugin-social", "theme", "light");

        repo.deleteAllForPlugin("user-1", "plugin-social");

        assertTrue(repo.getAllForPlugin("user-1", "plugin-social").isEmpty());
        assertEquals(1, repo.getAllForPlugin("user-2", "plugin-social").size());
    }

    @Test
    void deleteAllForPlugin_nonexistent_plugin_does_not_throw() {
        assertDoesNotThrow(() -> repo.deleteAllForPlugin("user-1", "inexistant"));
    }
}