package tech.nabor.api;

public interface I18n {
    // translate a key with optional arguments
    String t(String key, Object... args);

    // register the traductions of a module (called at each plugin initialize())
    void registerBundle(String bundleName, ClassLoader classLoader);

    // change the active locale
    void setLocale(String locale);

    String getLocale();
}
