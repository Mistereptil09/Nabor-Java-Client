package tech.nabor;

import tech.nabor.api.ConnectedUser;
import tech.nabor.api.EventBus;
import tech.nabor.api.I18n;
import tech.nabor.api.NaborHttpClient;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.app.AppEventBus;
import tech.nabor.app.AppI18n;
import tech.nabor.app.AppNaborHttpClient;
import tech.nabor.app.AppNaborReporter;
import tech.nabor.app.AppPluginContext;
import tech.nabor.app.AppSqliteRepository;
import tech.nabor.app.PluginRegistry;
import tech.nabor.app.db.DatabaseManager;
import tech.nabor.ui.UiNaborReporter;


public final class Bootstrap {

    private static final String DB_FILE = "nabor.db";

    private Bootstrap() {
    }

    public static AppContext create() {
        SettingsStore settings = new SettingsStore();
        String locale = settings.get("locale", "fr");

       
        I18n i18n = new AppI18n(locale);

        DatabaseManager dbManager = new DatabaseManager(DB_FILE, i18n);
        SqliteRepository db = new AppSqliteRepository(dbManager);

        EventBus eventBus = new AppEventBus();
        NaborHttpClient httpClient = new AppNaborHttpClient();
        
        NaborReporter reporter = new UiNaborReporter(new AppNaborReporter());

        ConnectedUser connectedUser = new MutableConnectedUser();

        PluginContext ctx =
                new AppPluginContext(httpClient, db, connectedUser, i18n, eventBus, reporter);

        PluginRegistry registry = new PluginRegistry();

        return new AppContext(ctx, registry, settings);
    }
}
