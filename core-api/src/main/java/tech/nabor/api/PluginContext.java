package tech.nabor.api;

import tech.nabor.api.error.NaborReporter;

public interface PluginContext {
    NaborHttpClient getHttpClient();     // configured client with the token
    SqliteRepository getDb();            // local single table db access
    // ServiceRegistry getServices();       // local joint table db access (not sure about wether it's usefull)
    ConnectedUser getConnectedUser();    // the whole session object of the currently connected user (on the java client)
    I18n getI18n();                      // I18n traduction from the app
    EventBus getEventBus();              // communication between plugins
    NaborReporter getReporter();         // interface to show a message to the user on the ui (error, info or warning)
}