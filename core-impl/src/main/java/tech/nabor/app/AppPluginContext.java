package tech.nabor.app;

import tech.nabor.api.*;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.I18n;

import javax.imageio.spi.ServiceRegistry;

public class AppPluginContext implements PluginContext {

    private final SqliteRepository db;
    private final NaborHttpClient  httpClient;
    private final ConnectedUser    connectedUser;
    private final EventBus         eventBus;
    private final NaborReporter naborReporter;
    private final I18n             i18n;

    public AppPluginContext(
            SqliteRepository db,
            NaborHttpClient  httpClient,
            ConnectedUser    connectedUser,
            EventBus         eventBus,
            NaborReporter naborReporter,
            I18n             i18n
    ) {
        this.db            = db;
        this.httpClient    = httpClient;
        this.connectedUser = connectedUser;
        this.eventBus      = eventBus;
        this.naborReporter = naborReporter;
        this.i18n          = i18n;
    }

    @Override public SqliteRepository getDb()             { return db; }
    @Override public NaborHttpClient  getHttpClient()     { return httpClient; }
    @Override public ConnectedUser    getConnectedUser()  { return connectedUser; }
    @Override public EventBus         getEventBus()       { return eventBus; }
    @Override public NaborReporter    getReporter()  { return naborReporter; }
    @Override public I18n             getI18n()           { return i18n; }
}