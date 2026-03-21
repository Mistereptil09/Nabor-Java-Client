package tech.nabor.app;

import tech.nabor.api.I18n;

import java.text.MessageFormat;
import java.util.*;

public class AppI18n implements I18n {

    private Locale locale;
    private final List<ResourceBundle> bundles = new ArrayList<>();

    public AppI18n(String locale) {
        this.locale = Locale.forLanguageTag(locale);
    }

    @Override
    public void registerBundle(String bundleName, ClassLoader classLoader) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(
                    "i18n/" + bundleName + "/messages",
                    locale,
                    classLoader
            );
            bundles.add(bundle);
        } catch (MissingResourceException e) {
            // bundle absent for this locale — we ignore silently
            // the plugin will show the brute key if the bundle is missing
        }
    }

    @Override
    public String t(String key, Object... args) {
        for (ResourceBundle bundle : bundles) {
            try {
                String pattern = bundle.getString(key);
                return args.length > 0
                        ? MessageFormat.format(pattern, args)
                        : pattern;
            } catch (MissingResourceException ignored) {
                // key missing in this bundle, we try the next one.
            }
        }
        return key; // fallback — returns the key value if we have no match
    }

    @Override
    public void setLocale(String locale) {
        this.locale = Locale.forLanguageTag(locale);
        // reloads all bundles for the new locale
        List<ResourceBundle> reloaded = new ArrayList<>();
        for (ResourceBundle bundle : bundles) {
            try {
                reloaded.add(ResourceBundle.getBundle(
                        bundle.getBaseBundleName(),
                        this.locale,
                        bundle.getClass().getClassLoader()
                ));
            } catch (MissingResourceException ignored) {}
        }
        bundles.clear();
        bundles.addAll(reloaded);
    }

    @Override
    public String getLocale() {
        return locale.toLanguageTag();
    }
}