package tech.nabor.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import tech.nabor.AppContext;
import tech.nabor.api.ConnectedUser;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.error.NaborException;
import tech.nabor.ui.dashboard.DashboardController;
import tech.nabor.ui.i18n.I18nManager;
import tech.nabor.ui.theme.ThemeManager;
import tech.nabor.api.model.user.User;


public class MainController {

    @FXML private HBox topBar;
    @FXML private VBox navBox;
    @FXML private StackPane contentArea;
    @FXML private Label userLabel;
    @FXML private Label onlineLabel;
    @FXML private Label placeholderLabel;

    private AppContext app;
    private I18nManager i18n;
    private ThemeManager themeManager;

    private Button langButton;
    private Button incidentsNavButton;
    private Button statsNavButton;
    private Button settingsNavButton;

    private Runnable onDisconnect;

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    // Track plugin nav buttons separately so they can be removed/rebuilt
    private final List<Button> pluginNavButtons = new ArrayList<>();

    public void init(AppContext app, I18nManager i18n) {
        this.app = app;
        this.i18n = i18n;

        // Rebuild plugin nav when a plugin is loaded or unloaded
        app.pluginContext().getEventBus().subscribe(
                tech.nabor.app.PluginRegistry.PLUGINS_CHANGED,
                payload -> Platform.runLater(this::rebuildPluginNav));

        // Show network errors to the user
        app.pluginContext().getEventBus().subscribe(
                tech.nabor.app.AppNaborHttpClient.NETWORK_ERROR,
                payload -> Platform.runLater(() ->
                        app.pluginContext().getReporter().reportWarning(
                                "Network: " + (payload != null ? payload.toString() : "unknown error"))));

        ConnectedUser user = app.pluginContext().getConnectedUser();
        String displayUserText = user.getEmail() + "  ·  " + user.getRole();

        if (user.getUserId() != null) {
            Optional<User> localUser = app.pluginContext().getDb().users().findById(user.getUserId());
            if (localUser.isPresent()) {
                User u = localUser.get();
                String name = (u.firstName() + " " + u.lastName()).trim();
                if (!name.isEmpty()) {
                    displayUserText = name + " (" + u.email() + ")  ·  " + u.role();
                }
            }
        }
        userLabel.setText(displayUserText);

        installLanguageToggle();
        installThemeToggle();
        installDisconnectButton();
        setupNavigation();

        i18n.onLocaleChange(this::applyTexts);
        applyTexts();
    }

    private void installLanguageToggle() {
        langButton = new Button();
        langButton.getStyleClass().add("theme-button");
        langButton.setOnAction(e -> i18n.toggle());
        topBar.getChildren().add(langButton);
    }

    private void installThemeToggle() {
        Button themeButton = new Button("🎨");
        themeButton.getStyleClass().add("theme-button");
        themeButton.setOnAction(e -> themeManager.toggle());
        topBar.getChildren().add(themeButton);
    }

    private void installDisconnectButton() {
        Button disconnectBtn = new Button("🚪");
        disconnectBtn.getStyleClass().add("theme-button");
        disconnectBtn.setOnAction(e -> {
            if (onDisconnect != null) onDisconnect.run();
        });
        topBar.getChildren().add(disconnectBtn);
    }

    private void setupNavigation() {
        // Built-in screens (incidents, stats, settings)
        Node incidentsView = loadScreen("/fxml/incidents-view.fxml");
        if (incidentsView != null) {
            incidentsNavButton = addNavItem("Incidents", incidentsView);
            showView(incidentsView);
        }

        Node dashboardView = loadScreen("/fxml/dashboard-view.fxml");
        if (dashboardView != null) {
            statsNavButton = addNavItem("Dashboard", dashboardView);
        }

        Node settingsView = loadScreen("/fxml/settings-view.fxml");
        if (settingsView != null) {
            settingsNavButton = addNavItem("Réglages", settingsView);
        }

        // Plugin-provided screens
        rebuildPluginNav();
    }

    private void rebuildPluginNav() {
        navBox.getChildren().removeAll(pluginNavButtons);
        pluginNavButtons.clear();

        String userId = app.pluginContext().getConnectedUser().getUserId();
        for (NaborPlugin plugin : app.registry().getPlugins()) {
            // Only show plugins that are enabled for this user
            boolean enabled = app.pluginContext().getDb().pluginStates()
                    .findByUserAndPlugin(userId, plugin.getId())
                    .map(s -> s.enabled())
                    .orElse(true); // default: enabled
            if (!enabled) continue;

            Optional<Node> view = plugin.getView();
            if (view.isPresent()) {
                Button btn = addNavItem(plugin.getDisplayName(), view.get());
                pluginNavButtons.add(btn);
            }
        }
    }

    private Node loadScreen(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof IncidentsController incidents) {
                incidents.init(app, i18n);
            } else if (controller instanceof DashboardController dashboard) {
                dashboard.init(app, i18n);
                return dashboard.getView();
            } else if (controller instanceof SettingsController settings) {
                settings.init(app, i18n, themeManager);
            }
            return root;
        } catch (Exception e) {
            app.pluginContext().getReporter().reportError(new NaborException(
                    NaborException.Kind.VALIDATION, "Échec du chargement de l'écran : " + fxmlPath, e));
            return null;
        }
    }

    private Button addNavItem(String label, Node view) {
        Button navButton = new Button(label);
        navButton.setMaxWidth(Double.MAX_VALUE);
        navButton.getStyleClass().add("nav-button");
        navButton.setOnAction(e -> showView(view));
        navBox.getChildren().add(navButton);
        return navButton;
    }

    private void showView(Node view) {
        contentArea.getChildren().setAll(view);
    }

    private void applyTexts() {
        ConnectedUser user = app.pluginContext().getConnectedUser();
        onlineLabel.setText(user.isOnline() ? i18n.t("topbar.online") : i18n.t("topbar.offline"));
        placeholderLabel.setText(i18n.t("shell.placeholder"));
        langButton.setText("🌐 " + i18n.locale().toUpperCase());
        if (incidentsNavButton != null) {
            incidentsNavButton.setText(i18n.t("nav.incidents"));
        }
        if (statsNavButton != null) {
            statsNavButton.setText(i18n.t("nav.stats"));
        }
        if (settingsNavButton != null) {
            settingsNavButton.setText(i18n.t("nav.settings"));
        }
    }
}
