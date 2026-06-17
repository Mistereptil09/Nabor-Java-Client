package tech.nabor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.time.Instant;

import tech.nabor.api.ConnectedUser;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.error.NaborException;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.model.enums.MessagePolicy;
import tech.nabor.api.model.enums.UserRole;
import tech.nabor.api.model.enums.Visibility;
import tech.nabor.api.model.local.LocalAccount;
import tech.nabor.api.model.user.User;
import tech.nabor.app.AppNaborHttpClient;
import tech.nabor.service.AuthService;
import tech.nabor.ui.LoginController;
import tech.nabor.ui.MainController;
import tech.nabor.ui.UiNaborReporter;
import tech.nabor.ui.i18n.I18nManager;
import tech.nabor.ui.theme.ThemeManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Optional;


public class NaborApp extends Application {

    private AppContext app;
    private I18nManager i18n;
    private ThemeManager theme;
    private StackPane contentHolder;
    private AuthService authService;
    private String refreshToken;

    @Override
    public void init() {
        app = Bootstrap.create();
    }

    @Override
    public void start(Stage stage) throws Exception {
        StackPane sceneRoot = new StackPane();
        contentHolder = new StackPane();
        sceneRoot.getChildren().add(contentHolder);

        NaborReporter reporter = app.pluginContext().getReporter();
        if (reporter instanceof UiNaborReporter uiReporter) {
            uiReporter.attachToastLayer(sceneRoot);
        }

        Scene scene = new Scene(sceneRoot, 1024, 720);

        theme = new ThemeManager(scene, app.settings());
        theme.applySaved();

        i18n = new I18nManager(app.settings());

        List<LocalAccount> accounts = app.pluginContext().getDb()
                .localAccounts().findAll();
        showLogin(accounts);

        stage.setTitle("Nabor Services");
        stage.setScene(scene);
        stage.show();
    }

    private void deleteAccount(LocalAccount account) {
        app.pluginContext().getDb().localAccounts().delete(account.userId());
    }

    private void disconnect() {
        if (app.pluginContext().getHttpClient() instanceof AppNaborHttpClient httpClient) {
            httpClient.setToken(null);
        }
        refreshToken = null;

        try {
            List<LocalAccount> accounts = app.pluginContext().getDb()
                    .localAccounts().findAll();
            showLogin(accounts);
        } catch (Exception e) {
            System.err.println("[Auth] Failed to return to login: " + e.getMessage());
        }
    }

    private void showLogin(List<LocalAccount> accounts) throws Exception {
        authService = new AuthService(app.pluginContext().getHttpClient());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-view.fxml"));
        Parent root = loader.load();
        LoginController controller = loader.getController();
        controller.init(authService, i18n, this::onAuthenticated, this::deleteAccount, accounts);
        contentHolder.getChildren().setAll(root);
    }

    private void onAuthenticated(AuthService.Session session) {
        if (app.pluginContext().getHttpClient() instanceof AppNaborHttpClient httpClient) {
            httpClient.setToken(session.accessToken());
            this.refreshToken = session.refreshToken();
        }

        String[] profile = fetchUserProfile(session.accessToken());
        // profile = [userId, email, displayName, role]

        if (app.pluginContext().getHttpClient() instanceof AppNaborHttpClient httpClient) {
            if (session.refreshToken() != null && !session.refreshToken().isBlank()) {
                String userId = profile[0];
                httpClient.setTokenRefresher(() -> {
                    System.out.println("[Auth] Token expired, refreshing...");
                    var newSession = authService.refresh(refreshToken);
                    refreshToken = newSession.refreshToken();
                    app.pluginContext().getDb().localAccounts().findById(userId)
                            .ifPresent(acct -> app.pluginContext().getDb().localAccounts()
                                    .save(new LocalAccount(acct.userId(), acct.email(),
                                            acct.displayName(), acct.isActive(),
                                            acct.lastLoginAt(), refreshToken)));
                    System.out.println("[Auth] Token refreshed successfully");
                    return newSession.accessToken();
                });
            }
        }

        ConnectedUser user = app.pluginContext().getConnectedUser();
        if (user instanceof MutableConnectedUser mutable) {
            mutable.connect(profile[0], profile[1], profile[3]);
        }

        ensureLocalUser(profile[0], profile[1], profile[2]);

        app.registry().loadAll(app.pluginContext());

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.setThemeManager(theme);
            controller.setOnDisconnect(this::disconnect);
            controller.init(app, i18n);
            contentHolder.getChildren().setAll(root);
        } catch (Exception e) {
            app.pluginContext().getReporter().reportError(new NaborException(
                    NaborException.Kind.HTTP_ERROR, "Échec du chargement de l'interface principale", e));
        }
    }

    /** Returns [userId, email, displayName, role] from /users/me or JWT fallback. */
    private String[] fetchUserProfile(String token) {
        if ("dev-access-token".equals(token))
            return new String[]{"dev-admin-001", "admin@nabor.tech", "Dev Admin", "admin"};

        try {
            String json = app.pluginContext().getHttpClient().get("/users/me");
            JsonNode p = new ObjectMapper().readTree(json);
            String name = (p.path("firstName").asText("") + " " + p.path("lastName").asText("")).trim();
            return new String[]{
                    p.path("id").asText(),
                    p.path("email").asText(""),
                    name.isEmpty() ? "User" : name,
                    p.path("role").asText("resident")};
        } catch (Exception e) {
            System.err.println("[Auth] " + diagnoseError(e) + " — using JWT fallback");
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    JsonNode p = new ObjectMapper().readTree(payloadJson);
                    return new String[]{
                            p.path("sub").asText("offline"),
                            "offline",
                            "Offline User",
                            p.path("role").asText("resident")};
                }
            } catch (Exception ignored) {}
            return new String[]{"offline", "offline", "Offline User", "resident"};
        }
    }

    private void ensureLocalUser(String userId, String email, String displayName) {
        SqliteRepository db = app.pluginContext().getDb();
        db.localAccounts().save(new LocalAccount(
                userId, email, displayName, true, Instant.now(), refreshToken));
        db.localAccounts().setActive(userId);
    }


    /** Maps an exception to a short diagnostic label. */
    private static String diagnoseError(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof java.net.ConnectException) return "API unreachable";
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) return "Request timed out";
            if (cause instanceof java.net.UnknownHostException) return "DNS resolution failed";
            if (cause instanceof java.io.IOException) {
                String msg = cause.getMessage();
                if (msg != null && (msg.contains("401") || msg.contains("403"))) return "Token rejected (HTTP 401/403)";
                if (msg != null && msg.contains("500")) return "Server error (HTTP 500)";
                return "IO error: " + (msg != null ? msg : cause.getClass().getSimpleName());
            }
            cause = cause.getCause();
        }
        return "Unexpected error: " + e.getMessage();
    }

    @Override
    public void stop() {
        if (app != null) {
            app.registry().shutdownAll();
        }
    }
}
