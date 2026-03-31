// core-impl/src/test/java/tech/nabor/app/AppPluginContextTest.java
package tech.nabor.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.nabor.app.db.DatabaseManager;

import static org.junit.jupiter.api.Assertions.*;

class AppPluginContextTest {

    private AppPluginContext ctx;
    private AppEventBus eventBus;
    private AppConnectedUser connectedUser;
    private AppNaborReporter naborReporter;
    private AppNaborHttpClient httpClient;
    private AppSqliteRepository db;
    // private AppServiceRegistry services;
    private TestI18n i18n;

    @BeforeEach
    void setUp() throws Exception {
        java.io.File tmpDb = java.io.File.createTempFile("nabor_test_", ".db");
        tmpDb.deleteOnExit();

        DatabaseManager dbManager = new DatabaseManager(tmpDb.getAbsolutePath(), new TestI18n());
        db             = new AppSqliteRepository(dbManager);
        eventBus       = new AppEventBus(new TestI18n());
        connectedUser  = new AppConnectedUser("user-1", "a@test.com", "admin");
        naborReporter = new AppNaborReporter();
        httpClient     = new AppNaborHttpClient("http://localhost:3000", "token");
        // services       = new AppServiceRegistry(db);
        i18n           = new TestI18n();

        ctx = new AppPluginContext(db, httpClient, connectedUser,
                eventBus, naborReporter, i18n);
    }

    // ── getDb ─────────────────────────────────────────────────────────────────

    @Test
    void getDb_returns_db() {
        assertNotNull(ctx.getDb());
        assertSame(db, ctx.getDb());
    }

    // ── getHttpClient ─────────────────────────────────────────────────────────

    @Test
    void getHttpClient_returns_http_client() {
        assertNotNull(ctx.getHttpClient());
        assertSame(httpClient, ctx.getHttpClient());
    }

    // ── getConnectedUser ──────────────────────────────────────────────────────

    @Test
    void getConnectedUser_returns_user() {
        assertNotNull(ctx.getConnectedUser());
        assertEquals("user-1",      ctx.getConnectedUser().getUserId());
        assertEquals("a@test.com",  ctx.getConnectedUser().getEmail());
        assertEquals("admin",       ctx.getConnectedUser().getRole());
        assertTrue(ctx.getConnectedUser().isOnline());
    }

    // ── getEventBus ───────────────────────────────────────────────────────────

    @Test
    void getEventBus_returns_event_bus() {
        assertNotNull(ctx.getEventBus());
        assertSame(eventBus, ctx.getEventBus());
    }

    @Test
    void eventBus_is_functional_through_context() {
        java.util.List<Object> received = new java.util.ArrayList<>();
        ctx.getEventBus().subscribe("test.event", received::add);
        ctx.getEventBus().publish("test.event", "payload");

        assertEquals(1,         received.size());
        assertEquals("payload", received.getFirst());
    }

    // ── getErrorReporter ──────────────────────────────────────────────────────

    @Test
    void getErrorReporter_returns_error_reporter() {
        assertNotNull(ctx.getReporter());
        assertSame(naborReporter, ctx.getReporter());
    }

    @Test
    void errorReporter_does_not_throw() {
        assertDoesNotThrow(() -> {
            ctx.getReporter().reportInfo("Info test");
            ctx.getReporter().reportWarning("Warning test");
            ctx.getReporter().reportError(
                    new tech.nabor.api.error.NaborException(
                            tech.nabor.api.error.NaborException.Kind.DB_ERROR, "Test", null));
        });
    }

    // ── getI18n ───────────────────────────────────────────────────────────────

    @Test
    void getI18n_returns_i18n() {
        assertNotNull(ctx.getI18n());
        assertSame(i18n, ctx.getI18n());
    }


    // ── ConnectedUser ─────────────────────────────────────────────────────────

    @Test
    void connected_user_online_status_is_mutable() {
        assertTrue(connectedUser.isOnline());
        connectedUser.setOnline(false);
        assertFalse(ctx.getConnectedUser().isOnline());
    }
}