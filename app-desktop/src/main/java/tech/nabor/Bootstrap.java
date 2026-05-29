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

/**
 * Câblage des dépendances de l'application desktop.
 *
 * <p>Reprend l'assemblage qui était dans l'ancien {@code Main} console et le
 * rend réutilisable / testable. N'instancie volontairement aucun composant
 * JavaFX : le bootstrap peut donc tourner hors du FX Application Thread
 * (typiquement dans {@code NaborApp.init()}).</p>
 *
 * <p>Les valeurs codées en dur (utilisateur connecté, locale) seront remplacées
 * aux étapes suivantes : la locale sera lue depuis {@code app_settings} (§10.4)
 * et l'utilisateur connecté proviendra du SSO QR (§7.2).</p>
 */
public final class Bootstrap {

    /** Fichier SQLite local — base embarquée offline-first (§7.3). */
    private static final String DB_FILE = "nabor.db";

    private Bootstrap() {
    }

    public static AppContext create() {
        // Réglages d'abord : on en lit la locale persistée (§7.6).
        SettingsStore settings = new SettingsStore();
        String locale = settings.get("locale", "fr");

        // I18n ensuite : DatabaseManager en a besoin pour ses messages.
        // Les bundles UI sont enregistrés par I18nManager (côté NaborApp).
        I18n i18n = new AppI18n(locale);

        DatabaseManager dbManager = new DatabaseManager(DB_FILE, i18n);
        SqliteRepository db = new AppSqliteRepository(dbManager);

        EventBus eventBus = new AppEventBus();
        NaborHttpClient httpClient = new AppNaborHttpClient();
        // Reporter visuel (toasts) qui délègue le log au reporter console existant.
        // NaborApp y branchera la couche d'overlay une fois la fenêtre construite.
        NaborReporter reporter = new UiNaborReporter(new AppNaborReporter());

        // Utilisateur connecté : détenteur mutable, renseigné après le SSO (étape 6).
        ConnectedUser connectedUser = new MutableConnectedUser();

        PluginContext ctx =
                new AppPluginContext(httpClient, db, connectedUser, i18n, eventBus, reporter);

        PluginRegistry registry = new PluginRegistry();

        return new AppContext(ctx, registry, settings);
    }
}
