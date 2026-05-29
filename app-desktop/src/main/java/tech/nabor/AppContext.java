package tech.nabor;

import tech.nabor.api.PluginContext;
import tech.nabor.app.PluginRegistry;

/**
 * Conteneur immuable des objets cœur de l'application, assemblés au démarrage
 * par {@link Bootstrap}. Le {@link PluginContext} donne accès à tous les
 * services (DB, i18n, EventBus, HttpClient, Reporter, utilisateur connecté) ;
 * le {@link PluginRegistry} gère le cycle de vie des plugins ; le
 * {@link SettingsStore} persiste les réglages UI locaux (thème, locale).
 */
public record AppContext(PluginContext pluginContext, PluginRegistry registry, SettingsStore settings) {
}
