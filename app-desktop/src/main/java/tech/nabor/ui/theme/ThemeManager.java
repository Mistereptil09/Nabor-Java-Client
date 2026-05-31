package tech.nabor.ui.theme;

import java.util.Objects;

import javafx.scene.Scene;
import tech.nabor.SettingsStore;


public class ThemeManager {

    public enum Theme {
        DEFAULT("default", "Clair", "/css/default.css"),
        DARK("dark", "Sombre", "/css/dark.css");

        private final String id;
        private final String label;
        private final String cssPath;

        Theme(String id, String label, String cssPath) {
            this.id = id;
            this.label = label;
            this.cssPath = cssPath;
        }

        public String id() { return id; }
        public String label() { return label; }

        static Theme fromId(String id) {
            for (Theme t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
            return DEFAULT;
        }
    }

    private static final String SETTINGS_KEY = "active_theme";
    private static final String BASE_CSS = "/css/base.css";

    private final Scene scene;
    private final SettingsStore settings;
    private Theme current;

    public ThemeManager(Scene scene, SettingsStore settings) {
        this.scene = scene;
        this.settings = settings;
    }

    public void applySaved() {
        apply(Theme.fromId(settings.get(SETTINGS_KEY, Theme.DEFAULT.id())));
    }

    public void apply(Theme theme) {
        scene.getStylesheets().setAll(resource(BASE_CSS), resource(theme.cssPath));
        this.current = theme;
        settings.put(SETTINGS_KEY, theme.id());
    }

    public void toggle() {
        apply(current == Theme.DEFAULT ? Theme.DARK : Theme.DEFAULT);
    }

    public Theme current() {
        return current;
    }

    private String resource(String path) {
        return Objects.requireNonNull(getClass().getResource(path),
                "Ressource CSS introuvable : " + path).toExternalForm();
    }
}
