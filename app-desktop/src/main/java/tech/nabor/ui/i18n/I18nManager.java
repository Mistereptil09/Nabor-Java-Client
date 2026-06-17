package tech.nabor.ui.i18n;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import tech.nabor.SettingsStore;


public class I18nManager {

    private static final String SETTINGS_KEY = "locale";
    private static final String BUNDLE = "i18n/app/messages";

    private final SettingsStore settings;
    private final List<Runnable> listeners = new ArrayList<>();

    private Locale locale;
    private ResourceBundle bundle;

    public I18nManager(SettingsStore settings) {
        this.settings = settings;
        this.locale = Locale.forLanguageTag(settings.get(SETTINGS_KEY, "fr"));
        reload();
    }

    private void reload() {
        bundle = ResourceBundle.getBundle(BUNDLE, locale, getClass().getClassLoader());
    }

    public String t(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            return args.length > 0 ? MessageFormat.format(pattern, args) : pattern;
        } catch (MissingResourceException e) {
            return key; 
        }
    }

    public String locale() {
        return locale.toLanguageTag();
    }

    public void setLocale(String languageTag) {
        this.locale = Locale.forLanguageTag(languageTag);
        reload();
        settings.put(SETTINGS_KEY, languageTag);
        listeners.forEach(Runnable::run);
    }

    public void toggle() {
        setLocale(locale().startsWith("fr") ? "en" : "fr");
    }

    public void onLocaleChange(Runnable listener) {
        listeners.add(listener);
    }
}
