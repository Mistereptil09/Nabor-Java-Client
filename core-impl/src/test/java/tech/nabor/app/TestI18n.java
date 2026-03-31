package tech.nabor.app;

import tech.nabor.api.I18n;

public class TestI18n implements I18n {
    @Override public String t(String key, Object... args) { return key; }
    @Override public void registerBundle(String bundleName, ClassLoader classLoader) {}
    @Override public void setLocale(String locale) {}
    @Override public String getLocale() { return "fr"; }
}