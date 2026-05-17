package tech.nabor.app;

import tech.nabor.api.*;
import tech.nabor.api.error.NaborReporter;

public class AppPluginContext implements PluginContext {
    private final NaborHttpClient httpClient;
    private final SqliteRepository db;
    private final ConnectedUser connectedUser;
    private final I18n i18n;
    private final EventBus eventBus;
    private final NaborReporter reporter;

    public AppPluginContext(
            NaborHttpClient httpClient,
            SqliteRepository db,
            ConnectedUser connectedUser,
            I18n i18n,
            EventBus eventBus,
            NaborReporter reporter) {
        this.httpClient = httpClient;
        this.db = db;
        this.connectedUser = connectedUser;
        this.i18n = i18n;
        this.eventBus = eventBus;
        this.reporter = reporter;
    }

    @Override
    public NaborHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    public SqliteRepository getDb() {
        return db;
    }

    @Override
    public ConnectedUser getConnectedUser() {
        return connectedUser;
    }

    @Override
    public I18n getI18n() {
        return i18n;
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public NaborReporter getReporter() {
        return reporter;
    }
}
