package tech.nabor.ui;

import java.util.Optional;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.StringConverter;
import tech.nabor.AppContext;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.service.PluginManagerService;
import tech.nabor.service.PluginManagerService.PluginView;
import tech.nabor.service.UpdateService;
import tech.nabor.ui.i18n.I18nManager;
import tech.nabor.ui.theme.ThemeManager;


public class SettingsController {

    @FXML private Label screenTitle;
    @FXML private Label appearanceTitle;
    @FXML private VBox appearanceBox;
    @FXML private Label pluginsTitle;
    @FXML private Label updatesTitle;
    @FXML private Label dangerTitle;
    @FXML private Label versionLabel;
    @FXML private Label dangerHint;
    @FXML private Button checkUpdateButton;
    @FXML private Button uninstallAppButton;
    @FXML private TableView<PluginView> pluginTable;
    @FXML private TableColumn<PluginView, String> pluginNameCol;
    @FXML private TableColumn<PluginView, String> pluginIdCol;
    @FXML private TableColumn<PluginView, String> pluginStateCol;
    @FXML private TableColumn<PluginView, Void> pluginActionsCol;

    private AppContext app;
    private I18nManager i18n;
    private ThemeManager theme;
    private NaborReporter reporter;
    private PluginManagerService plugins;
    private UpdateService updates;

    // Libellés de la section apparence, rafraîchis au changement de langue.
    private Label themeRowLabel;
    private Label primaryRowLabel;
    private Label accentRowLabel;
    private Label fontRowLabel;
    private Label sizeRowLabel;
    private Label densityRowLabel;
    private Button themeToggleButton;
    private Button resetButton;
    private ComboBox<ThemeManager.Density> densityBox;

    public void init(AppContext app, I18nManager i18n, ThemeManager theme) {
        this.app = app;
        this.i18n = i18n;
        this.theme = theme;
        this.reporter = app.pluginContext().getReporter();
        this.plugins = new PluginManagerService(app.pluginContext(), app.registry());
        this.updates = new UpdateService(app.pluginContext().getHttpClient());

        setupColumns();
        buildAppearanceSection();
        i18n.onLocaleChange(this::applyTexts);
        applyTexts();
        refresh();
    }

    private void setupColumns() {
        pluginNameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().displayName()));
        pluginIdCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().id()));
        pluginStateCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().enabled() ? i18n.t("settings.plugin.enabled") : i18n.t("settings.plugin.disabled")));
        pluginActionsCol.setCellFactory(col -> new ActionCell());
    }


    private void buildAppearanceSection() {
        themeRowLabel = new Label();
        themeToggleButton = new Button();
        themeToggleButton.getStyleClass().add("nav-button");
        themeToggleButton.setOnAction(e -> {
            theme.toggle();
            applyTexts();
        });

        primaryRowLabel = new Label();
        ColorPicker primaryPicker = new ColorPicker(parseColor(theme.primaryColor(), "#0F2A5E"));
        primaryPicker.setOnAction(e -> theme.setPrimaryColor(toHex(primaryPicker.getValue())));

        accentRowLabel = new Label();
        ColorPicker accentPicker = new ColorPicker(parseColor(theme.accentColor(), "#F7931E"));
        accentPicker.setOnAction(e -> theme.setAccentColor(toHex(accentPicker.getValue())));

        fontRowLabel = new Label();
        ComboBox<String> fontBox = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
        String currentFont = theme.fontFamily().isBlank() ? Font.getDefault().getFamily() : theme.fontFamily();
        fontBox.setValue(currentFont);
        fontBox.setOnAction(e -> theme.setFontFamily(fontBox.getValue()));

        sizeRowLabel = new Label();
        Slider sizeSlider = new Slider(11, 18, theme.fontSize());
        sizeSlider.setMajorTickUnit(1);
        sizeSlider.setMinorTickCount(0);
        sizeSlider.setSnapToTicks(true);
        sizeSlider.setShowTickLabels(true);
        Label sizeValue = new Label(theme.fontSize() + " px");
        sizeSlider.valueProperty().addListener((o, a, b) -> {
            int size = b.intValue();
            sizeValue.setText(size + " px");
            theme.setFontSize(size);
        });

        densityRowLabel = new Label();
        densityBox = new ComboBox<>(FXCollections.observableArrayList(ThemeManager.Density.values()));
        densityBox.setValue(theme.density());
        densityBox.setConverter(new StringConverter<>() {
            @Override public String toString(ThemeManager.Density d) {
                return d == null ? "" : i18n.t("settings.density." + d.id());
            }
            @Override public ThemeManager.Density fromString(String s) { return null; }
        });
        densityBox.setOnAction(e -> theme.setDensity(densityBox.getValue()));

        resetButton = new Button();
        resetButton.getStyleClass().add("nav-button");
        resetButton.setOnAction(e -> {
            theme.resetCustomizations();
            primaryPicker.setValue(parseColor("", "#0F2A5E"));
            accentPicker.setValue(parseColor("", "#F7931E"));
            fontBox.setValue(Font.getDefault().getFamily());
            sizeSlider.setValue(13);
            densityBox.setValue(ThemeManager.Density.COMFORTABLE);
            reporter.reportInfo(i18n.t("settings.appearance.reset.toast"));
        });

        appearanceBox.getChildren().setAll(
                row(themeRowLabel, themeToggleButton),
                row(primaryRowLabel, primaryPicker),
                row(accentRowLabel, accentPicker),
                row(fontRowLabel, fontBox),
                row(sizeRowLabel, new HBox(10, sizeSlider, sizeValue)),
                row(densityRowLabel, densityBox),
                row(new Label(), resetButton));
    }

    private HBox row(Label label, javafx.scene.Node control) {
        label.getStyleClass().add("detail-meta");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(12, label, spacer, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Color parseColor(String hex, String fallback) {
        try {
            return Color.web(hex.isBlank() ? fallback : hex);
        } catch (IllegalArgumentException e) {
            return Color.web(fallback);
        }
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private void applyTexts() {
        screenTitle.setText(i18n.t("settings.title"));
        appearanceTitle.setText(i18n.t("settings.appearance.title"));
        themeRowLabel.setText(i18n.t("settings.appearance.theme"));
        themeToggleButton.setText(i18n.t(theme.current() == ThemeManager.Theme.DARK
                ? "settings.appearance.theme.dark" : "settings.appearance.theme.light"));
        primaryRowLabel.setText(i18n.t("settings.appearance.primary"));
        accentRowLabel.setText(i18n.t("settings.appearance.accent"));
        fontRowLabel.setText(i18n.t("settings.appearance.font"));
        sizeRowLabel.setText(i18n.t("settings.appearance.size"));
        densityRowLabel.setText(i18n.t("settings.appearance.density"));
        resetButton.setText(i18n.t("settings.appearance.reset"));
        densityBox.setItems(FXCollections.observableArrayList(ThemeManager.Density.values()));
        densityBox.setValue(theme.density());
        pluginsTitle.setText(i18n.t("settings.plugins.title"));
        updatesTitle.setText(i18n.t("settings.updates.title"));
        dangerTitle.setText(i18n.t("settings.danger.title"));
        dangerHint.setText(i18n.t("settings.danger.hint"));
        checkUpdateButton.setText(i18n.t("settings.updates.check"));
        uninstallAppButton.setText(i18n.t("settings.app.uninstall"));
        versionLabel.setText(i18n.t("settings.version", UpdateService.CURRENT_VERSION));
        pluginNameCol.setText(i18n.t("settings.col.name"));
        pluginIdCol.setText(i18n.t("settings.col.id"));
        pluginStateCol.setText(i18n.t("settings.col.state"));
        pluginActionsCol.setText(i18n.t("settings.col.actions"));
        pluginTable.refresh();
    }

    private void refresh() {
        pluginTable.setItems(FXCollections.observableArrayList(plugins.list()));
    }

    @FXML
    private void onCheckUpdate() {
        UpdateService.UpdateInfo info = updates.checkForUpdate();
        if (info.available()) {
            reporter.reportInfo(i18n.t("settings.updates.available", info.latestVersion()));
        } else {
            reporter.reportInfo(i18n.t("settings.updates.uptodate"));
        }
    }

    @FXML
    private void onUninstallApp() {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle(i18n.t("settings.app.uninstall"));
        alert.setHeaderText(null);
        alert.setContentText(i18n.t("settings.app.uninstall.confirm"));
        if (pluginTable.getScene() != null) {
            alert.getDialogPane().getStylesheets().setAll(pluginTable.getScene().getStylesheets());
        }
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // TODO réel : supprimer ~/.nabor (settings + base SQLite) puis quitter.
            reporter.reportWarning(i18n.t("settings.app.uninstall.done"));
        }
    }

    private class ActionCell extends TableCell<PluginView, Void> {
        private final Button toggleButton = new Button();
        private final HBox box = new HBox(toggleButton);

        ActionCell() {
            box.setAlignment(Pos.CENTER_LEFT);
            toggleButton.getStyleClass().add("nav-button");

            toggleButton.setOnAction(e -> {
                PluginView pv = getCurrent();
                plugins.setEnabled(pv.id(), !pv.enabled());
                app.pluginContext().getEventBus().publish(
                        tech.nabor.app.PluginRegistry.PLUGINS_CHANGED, pv.id());
                reporter.reportInfo(i18n.t(pv.enabled()
                        ? "settings.plugin.disabled.toast" : "settings.plugin.enabled.toast"));
                refresh();
            });
        }

        private PluginView getCurrent() {
            return getTableView().getItems().get(getIndex());
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
                return;
            }
            PluginView pv = getCurrent();
            toggleButton.setText(pv.enabled() ? i18n.t("settings.plugin.disable") : i18n.t("settings.plugin.enable"));
            setGraphic(box);
        }
    }
}
