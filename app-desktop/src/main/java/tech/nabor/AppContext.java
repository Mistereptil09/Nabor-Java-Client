package tech.nabor;

import tech.nabor.api.PluginContext;
import tech.nabor.app.PluginRegistry;


public record AppContext(PluginContext pluginContext, PluginRegistry registry, SettingsStore settings) {
}
