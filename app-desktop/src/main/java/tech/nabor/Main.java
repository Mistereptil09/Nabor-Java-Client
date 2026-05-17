package tech.nabor;

import tech.nabor.api.*;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.app.*;
import tech.nabor.app.db.DatabaseManager;

public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Nabor Plugin Test Runner");
        System.out.println("========================================\n");

        // Create I18n first (needed by DatabaseManager)
        I18n i18n = new AppI18n("en");
        
        // Create real database with schema initialization
        System.out.println("--- Initializing Database ---");
        DatabaseManager dbManager = new DatabaseManager("nabor-test.db", i18n);
        SqliteRepository db = new AppSqliteRepository(dbManager);
        System.out.println("Database initialized with schema\n");

        // Create other dependencies
        EventBus eventBus = new AppEventBus();
        ConnectedUser connectedUser = new AppConnectedUser("test-user-123", "test@nabor.tech", "resident");
        NaborHttpClient httpClient = new AppNaborHttpClient();
        NaborReporter reporter = new AppNaborReporter();

        // Create plugin context
        PluginContext ctx = new AppPluginContext(httpClient, db, connectedUser, i18n, eventBus, reporter);

        // Load and initialize all plugins
        PluginRegistry registry = new PluginRegistry();
        System.out.println("--- Loading Plugins ---");
        registry.loadAll(ctx);

        // Test the event bus
        System.out.println("\n--- Testing EventBus ---");
        eventBus.publish("test.ping", "Hello from Main!");

        // Wait a bit to see output
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Shutdown plugins
        System.out.println("\n--- Shutting Down ---");
        registry.shutdownAll();

        System.out.println("\n========================================");
        System.out.println("Plugin Test Complete!");
        System.out.println("========================================");
    }
}