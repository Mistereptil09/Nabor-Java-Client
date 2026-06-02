package tech.nabor.ui;

import java.util.Optional;

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
import tech.nabor.ui.i18n.I18nManager;
import tech.nabor.ui.theme.ThemeManager;


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
    private Button syncNavButton;
    private Button settingsNavButton;

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public void init(AppContext app, I18nManager i18n) {
        this.app = app;
        this.i18n = i18n;

        ConnectedUser user = app.pluginContext().getConnectedUser();
        userLabel.setText(user.getEmail() + "  ·  " + user.getRole());

        installLanguageToggle();
        installThemeToggle();
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

    private void setupNavigation() {
        Node incidentsView = loadScreen("/fxml/incidents-view.fxml");
        if (incidentsView != null) {
            incidentsNavButton = addNavItem("Incidents", incidentsView);
            showView(incidentsView); 
        }

        Node statsView = loadScreen("/fxml/statistics-view.fxml");
        if (statsView != null) {
            statsNavButton = addNavItem("Statistiques", statsView);
        }

        Node syncView = loadScreen("/fxml/sync-view.fxml");
        if (syncView != null) {
            syncNavButton = addNavItem("Synchronisation", syncView);
        }

        Node settingsView = loadScreen("/fxml/settings-view.fxml");
        if (settingsView != null) {
            settingsNavButton = addNavItem("Réglages", settingsView);
        }

        for (NaborPlugin plugin : app.registry().getPlugins()) {
            Optional<Node> view = plugin.getView();
            view.ifPresent(node -> addNavItem(plugin.getDisplayName(), node));
        }
    }

    private Node loadScreen(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof IncidentsController incidents) {
                incidents.init(app, i18n);
            } else if (controller instanceof StatisticsController statistics) {
                statistics.init(app, i18n);
            } else if (controller instanceof SyncController sync) {
                sync.init(app, i18n);
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
        if (syncNavButton != null) {
            syncNavButton.setText(i18n.t("nav.sync"));
        }
        if (settingsNavButton != null) {
            settingsNavButton.setText(i18n.t("nav.settings"));
        }
    }
}
