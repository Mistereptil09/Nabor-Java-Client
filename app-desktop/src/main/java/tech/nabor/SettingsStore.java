package tech.nabor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Réglages locaux de l'application (thème, locale, …), persistés dans
 * {@code ~/.nabor/settings.properties}.
 *
 * <p>Le cahier (§7.6) prévoit de stocker ces clés dans la table SQLite
 * {@code app_settings}. Cette table n'existe pas encore dans le schéma
 * (seul {@code app_locale_config} est présent), donc on persiste pour
 * l'instant dans un fichier de propriétés autonome.</p>
 *
 * <p>TODO : migrer vers {@code app_settings} (clés {@code active_theme},
 * {@code locale}) quand la table sera ajoutée côté core.</p>
 */
public class SettingsStore {

    private final Path file;
    private final Properties props = new Properties();

    public SettingsStore() {
        this(Path.of(System.getProperty("user.home"), ".nabor", "settings.properties"));
    }

    SettingsStore(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("[SettingsStore] Lecture impossible (" + file + ") : " + e.getMessage());
        }
    }

    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public void put(String key, String value) {
        props.setProperty(key, value);
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Nabor Desktop — réglages locaux");
            }
        } catch (IOException e) {
            System.err.println("[SettingsStore] Écriture impossible (" + file + ") : " + e.getMessage());
        }
    }
}
