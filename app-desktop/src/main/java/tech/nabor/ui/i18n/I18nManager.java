package tech.nabor.ui.i18n;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import tech.nabor.SettingsStore;

/**
 * Pilote l'internationalisation de l'UI (§10.4).
 *
 * <p>Volontairement <strong>autonome</strong> : il charge lui-même le bundle
 * {@code i18n/app/messages} via {@link ResourceBundle} (UTF-8 sous Java 9+) au
 * lieu de passer par {@code AppI18n}, dont la méthode {@code setLocale()} lève
 * un {@link NullPointerException} ({@code getBaseBundleName()} renvoie
 * {@code null} pour un {@code PropertyResourceBundle}). {@code AppI18n} reste
 * utilisé par les plugins et la couche DB.</p>
 *
 * <p>Persiste la langue dans {@link SettingsStore} (clé {@code locale}) et
 * notifie les écrans abonnés pour un rafraîchissement à chaud (rôle du
 * {@code LocaleChangeListener} du cahier).</p>
 *
 * <p>TODO : reconverger sur un i18n unique une fois {@code AppI18n.setLocale()}
 * corrigé côté core.</p>
 */
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
            return key; // fallback : on affiche la clé brute
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

    /** Bascule fr ↔ en. */
    public void toggle() {
        setLocale(locale().startsWith("fr") ? "en" : "fr");
    }

    /** Abonne un écran au changement de langue (pour rafraîchir ses textes). */
    public void onLocaleChange(Runnable listener) {
        listeners.add(listener);
    }
}
