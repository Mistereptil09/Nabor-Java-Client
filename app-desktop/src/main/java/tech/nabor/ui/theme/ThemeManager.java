package tech.nabor.ui.theme;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import javafx.scene.Scene;
import tech.nabor.SettingsStore;


public class ThemeManager {

    public enum Theme {
        DEFAULT("default", "/css/default.css"),
        DARK("dark", "/css/dark.css");

        private final String id;
        private final String cssPath;

        Theme(String id, String cssPath) {
            this.id = id;
            this.cssPath = cssPath;
        }

        public String id() { return id; }

        static Theme fromId(String id) {
            for (Theme t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
            return DEFAULT;
        }
    }

    public enum Density {
        COMPACT("compact", 0.85),
        COMFORTABLE("comfortable", 1.0),
        SPACIOUS("spacious", 1.2);

        private final String id;
        private final double factor;

        Density(String id, double factor) {
            this.id = id;
            this.factor = factor;
        }

        public String id() { return id; }

        static Density fromId(String id) {
            for (Density d : values()) {
                if (d.id.equals(id)) {
                    return d;
                }
            }
            return COMFORTABLE;
        }
    }

    private static final String KEY_THEME = "active_theme";
    private static final String KEY_PRIMARY = "custom_primary";
    private static final String KEY_ACCENT = "custom_accent";
    private static final String KEY_FONT = "custom_font";
    private static final String KEY_FONT_SIZE = "custom_font_size";
    private static final String KEY_DENSITY = "custom_density";

    private static final String BASE_CSS = "/css/base.css";

    private final Scene scene;
    private final SettingsStore settings;
    private Theme current;

    public ThemeManager(Scene scene, SettingsStore settings) {
        this.scene = scene;
        this.settings = settings;
    }


    public void applySaved() {
        apply(Theme.fromId(settings.get(KEY_THEME, Theme.DEFAULT.id())));
    }

    public void apply(Theme theme) {
        this.current = theme;
        settings.put(KEY_THEME, theme.id());
        rebuild();
    }

    public void toggle() {
        apply(current == Theme.DEFAULT ? Theme.DARK : Theme.DEFAULT);
    }

    public Theme current() {
        return current;
    }


    public String primaryColor()  { return settings.get(KEY_PRIMARY, ""); }
    public String accentColor()   { return settings.get(KEY_ACCENT, ""); }
    public String fontFamily()    { return settings.get(KEY_FONT, ""); }
    public int fontSize()         { return Integer.parseInt(settings.get(KEY_FONT_SIZE, "13")); }
    public Density density()      { return Density.fromId(settings.get(KEY_DENSITY, Density.COMFORTABLE.id())); }


    public void setPrimaryColor(String hex) { settings.put(KEY_PRIMARY, hex == null ? "" : hex); rebuild(); }
    public void setAccentColor(String hex)  { settings.put(KEY_ACCENT, hex == null ? "" : hex); rebuild(); }
    public void setFontFamily(String font)  { settings.put(KEY_FONT, font == null ? "" : font); rebuild(); }
    public void setFontSize(int size)       { settings.put(KEY_FONT_SIZE, String.valueOf(size)); rebuild(); }
    public void setDensity(Density d)       { settings.put(KEY_DENSITY, d.id()); rebuild(); }

    public void resetCustomizations() {
        settings.put(KEY_PRIMARY, "");
        settings.put(KEY_ACCENT, "");
        settings.put(KEY_FONT, "");
        settings.put(KEY_FONT_SIZE, "13");
        settings.put(KEY_DENSITY, Density.COMFORTABLE.id());
        rebuild();
    }


    private void rebuild() {
        Theme theme = current != null ? current : Theme.DEFAULT;
        scene.getStylesheets().setAll(
                resource(BASE_CSS),
                resource(theme.cssPath),
                generatedOverrideUri());
    }

    private String generatedOverrideUri() {
        StringBuilder css = new StringBuilder(".root {");
        if (!primaryColor().isBlank()) {
            css.append("-nabor-primary:").append(primaryColor()).append(';');
        }
        if (!accentColor().isBlank()) {
            css.append("-nabor-accent:").append(accentColor()).append(';');
        }
        if (!fontFamily().isBlank()) {
            css.append("-fx-font-family:\"").append(fontFamily()).append("\";");
        }
        css.append("-fx-font-size:").append(fontSize()).append("px;");
        css.append('}');

        double f = density().factor;
        css.append(".kpi-card{-fx-padding:").append(round(16 * f)).append(";}");
        css.append(".nav-button{-fx-padding:").append(round(10 * f)).append(' ')
                .append(round(14 * f)).append(";}");

        String encoded = Base64.getEncoder()
                .encodeToString(css.toString().getBytes(StandardCharsets.UTF_8));
        return "data:text/css;base64," + encoded;
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    private String resource(String path) {
        return Objects.requireNonNull(getClass().getResource(path),
                "Ressource CSS introuvable : " + path).toExternalForm();
    }
}
