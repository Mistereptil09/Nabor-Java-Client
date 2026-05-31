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
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import tech.nabor.AppContext;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.service.PluginManagerService;
import tech.nabor.service.PluginManagerService.PluginView;
import tech.nabor.service.UpdateService;
import tech.nabor.ui.i18n.I18nManager;


public class SettingsController {

    @FXML private Label screenTitle;
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
    private NaborReporter reporter;
    private PluginManagerService plugins;
    private UpdateService updates;

    public void init(AppContext app, I18nManager i18n) {
        this.app = app;
        this.i18n = i18n;
        this.reporter = app.pluginContext().getReporter();
        this.plugins = new PluginManagerService(app.pluginContext(), app.registry());
        this.updates = new UpdateService(app.pluginContext().getHttpClient());

        setupColumns();
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

    private void applyTexts() {
        screenTitle.setText(i18n.t("settings.title"));
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
        private final Button removeButton = new Button();
        private final HBox box = new HBox(8, toggleButton, removeButton);

        ActionCell() {
            box.setAlignment(Pos.CENTER_LEFT);
            toggleButton.getStyleClass().add("nav-button");
            removeButton.getStyleClass().add("danger-button");

            toggleButton.setOnAction(e -> {
                PluginView pv = getCurrent();
                plugins.setEnabled(pv.id(), !pv.enabled());
                reporter.reportInfo(i18n.t(pv.enabled()
                        ? "settings.plugin.disabled.toast" : "settings.plugin.enabled.toast"));
                refresh();
            });

            removeButton.setOnAction(e -> {
                PluginView pv = getCurrent();
                plugins.uninstall(pv.id());
                reporter.reportWarning(i18n.t("settings.plugin.uninstalled.toast", pv.displayName()));
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
            removeButton.setText(i18n.t("settings.plugin.uninstall"));
            setGraphic(box);
        }
    }
}
