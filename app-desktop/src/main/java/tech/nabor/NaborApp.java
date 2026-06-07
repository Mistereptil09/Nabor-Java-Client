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
            // Wire auto-refresh on 401
            if (session.refreshToken() != null && !session.refreshToken().isBlank()) {
                httpClient.setTokenRefresher(() -> {
                    System.out.println("[Auth] Token expired, refreshing...");
                    var newSession = authService.refresh(refreshToken);
                    // Store the rotated refresh token from server
                    refreshToken = newSession.refreshToken();
                    System.out.println("[Auth] Token refreshed successfully");
                    return newSession.accessToken();
                });
            }
        }

        User profileUser = fetchUserProfile(session.accessToken());

        ConnectedUser user = app.pluginContext().getConnectedUser();
        if (user instanceof MutableConnectedUser mutable) {
            mutable.connect(profileUser.id(), profileUser.email(), profileUser.role().name());
        }

        ensureLocalUser(profileUser);

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

    private User fetchUserProfile(String token) {
        if ("dev-access-token".equals(token)) {
            return new User(
                    "dev-admin-001", "Dev", "Admin", "admin@nabor.tech",
                    "", null, null, null,
                    Visibility.public_, null, MessagePolicy.open, i18n.locale(),
                    null, null, UserRole.admin,
                    Instant.now(), null, Instant.now(), null, null);
        }

        try {
            String profileJson = app.pluginContext().getHttpClient().get("/users/me");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode profile = mapper.readTree(profileJson);

            String userId = profile.path("id").asText();
            String firstName = profile.path("firstName").asText();
            String lastName = profile.path("lastName").asText();
            String email = profile.path("email").asText();

            String roleStr = profile.path("role").asText("resident").toLowerCase();
            UserRole userRole = switch (roleStr) {
                case "admin" -> UserRole.admin;
                case "moderator" -> UserRole.moderator;
                case "neighbourhood_rep" -> UserRole.neighbourhood_rep;
                default -> UserRole.resident;
            };

            return new User(
                    userId, firstName, lastName, email,
                    "", null, null, null,
                    Visibility.public_, null, MessagePolicy.open, i18n.locale(),
                    null, null, userRole,
                    Instant.now(), null, Instant.now(), null, null);
        } catch (Exception e) {
            System.err.println("Failed to fetch user profile, using local/offline fallback: " + e.getMessage());
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode payload = mapper.readTree(payloadJson);
                    String userId = payload.path("sub").asText();

                    Optional<User> local = app.pluginContext().getDb().users().findById(userId);
                    if (local.isPresent()) {
                        return local.get();
                    }

                    String roleStr = payload.path("role").asText("resident").toLowerCase();
                    UserRole userRole = switch (roleStr) {
                        case "admin" -> UserRole.admin;
                        case "moderator" -> UserRole.moderator;
                        case "neighbourhood_rep" -> UserRole.neighbourhood_rep;
                        default -> UserRole.resident;
                    };
                    String locale = payload.path("locale").asText("fr");
                    return new User(
                            userId, "Offline", "User", userId + "@offline.nabor.tech",
                            "", null, null, null,
                            Visibility.public_, null, MessagePolicy.open, locale,
                            null, null, userRole,
                            Instant.now(), null, Instant.now(), null, null);
                }
            } catch (Exception ex) {
                // Ignore and use final fallback
            }

            return new User(
                    "2", "Offline", "Fallback", "fallback@nabor.tech",
                    "", null, null, null,
                    Visibility.public_, null, MessagePolicy.open, i18n.locale(),
                    null, null, UserRole.resident,
                    Instant.now(), null, Instant.now(), null, null);
        }
    }

    private void ensureLocalUser(User profileUser) {
        SqliteRepository db = app.pluginContext().getDb();

        String displayName = (profileUser.firstName() + " " + profileUser.lastName()).trim();
        if (displayName.isEmpty()) {
            displayName = "User " + profileUser.id();
        }

        db.localAccounts().save(new LocalAccount(
                profileUser.id(), profileUser.email(), displayName, true,
                Instant.now(), refreshToken));
        db.localAccounts().setActive(profileUser.id());
    }


    @Override
    public void stop() {
        if (app != null) {
            app.registry().shutdownAll();
        }
    }
}
