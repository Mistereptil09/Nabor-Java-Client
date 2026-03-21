package tech.nabor.app;

import org.jdbi.v3.core.Jdbi;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.repository.*;
import tech.nabor.app.db.DatabaseManager;
import tech.nabor.app.db.repository.local.*;
import tech.nabor.app.db.repository.sync.*;

public class AppSqliteRepository implements SqliteRepository {

    private final Jdbi jdbi;

    // tables locales
    private final LocalAccountRepository localAccounts;
    private final AppLocaleConfigRepository localeConfigs;
    private final AppPluginStateRepository pluginStates;
    private final AppPluginConfigRepository pluginConfigs;

    // sync
    private final AppSyncChangelogRepository syncChangelog;
    private final AppSyncStateRepository syncState;
    private final AppPendingConflictRepository pendingConflicts;
    private final AppResolvedConflictRepository resolvedConflicts;

    // ... autres domaines ajoutés au fur et à mesure

    public AppSqliteRepository(DatabaseManager db) {
        this.jdbi = db.getJdbi();

        // instanciation de chaque repository avec le même jdbi
        this.localAccounts   = new AppLocalAccountRepository(jdbi);
        this.localeConfigs   = new AppLocaleConfigRepository(jdbi);
        this.pluginStates    = new AppPluginStateRepository(jdbi);
        this.pluginConfigs   = new AppPluginConfigRepository(jdbi);

        this.syncChangelog   = new AppSyncChangelogRepository(jdbi);
        this.syncState       = new AppSyncStateRepository(jdbi);
        this.pendingConflicts = new AppPendingConflictRepository(jdbi);
        this.resolvedConflicts = new AppResolvedConflictRepository(jdbi);
    }

    // tables locales
    @Override public LocalAccountRepository localAccounts()   { return localAccounts; }
    @Override public AppLocaleConfigRepository localeConfigs() { return localeConfigs; }
    @Override public AppPluginStateRepository pluginStates()  { return pluginStates; }
    @Override public AppPluginConfigRepository pluginConfigs() { return pluginConfigs; }

    // sync
    @Override public AppSyncChangelogRepository syncChangelog()     { return syncChangelog; }
    @Override public AppSyncStateRepository syncState()             { return syncState; }
    @Override public AppPendingConflictRepository pendingConflicts() { return pendingConflicts; }
    @Override public AppResolvedConflictRepository resolvedConflicts() { return resolvedConflicts; }
}