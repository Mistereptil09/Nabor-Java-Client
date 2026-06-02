package tech.nabor.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import tech.nabor.AppContext;
import tech.nabor.api.EventBus;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.service.SyncService;
import tech.nabor.ui.i18n.I18nManager;


public class SyncController {

    @FXML private Label screenTitle;
    @FXML private Label statusLabel;
    @FXML private Label conflictsTitle;
    @FXML private Label resolveHint;
    @FXML private Button simulateButton;
    @FXML private Button syncButton;
    @FXML private Button keepLocalButton;
    @FXML private Button keepRemoteButton;
    @FXML private TableView<PendingConflict> table;
    @FXML private TableColumn<PendingConflict, String> entityCol;
    @FXML private TableColumn<PendingConflict, String> fieldCol;
    @FXML private TableColumn<PendingConflict, String> localCol;
    @FXML private TableColumn<PendingConflict, String> remoteCol;

    private SyncService service;
    private I18nManager i18n;
    private NaborReporter reporter;
    private AppContext app;

    public void init(AppContext app, I18nManager i18n) {
        this.app = app;
        this.i18n = i18n;
        this.service = new SyncService(app.pluginContext());
        this.reporter = app.pluginContext().getReporter();

        setupColumns();
        i18n.onLocaleChange(this::applyTexts);
        applyTexts();
        refresh();

        EventBus bus = app.pluginContext().getEventBus();
        bus.subscribe("sync.completed", e -> Platform.runLater(this::refresh));
        bus.subscribe("sync.failed", e -> Platform.runLater(() ->
                reporter.reportWarning("Sync échouée : " + e)
        ));
    }

    private void setupColumns() {
        entityCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().tableName()));
        fieldCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().fieldName()));
        localCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().localValue()));
        remoteCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().remoteValue()));
    }

    private void applyTexts() {
        screenTitle.setText(i18n.t("sync.title"));
        simulateButton.setText(i18n.t("sync.simulate"));
        syncButton.setText(i18n.t("sync.now"));
        conflictsTitle.setText(i18n.t("sync.conflicts.title"));
        resolveHint.setText(i18n.t("sync.resolve.hint"));
        keepLocalButton.setText(i18n.t("sync.keep.local"));
        keepRemoteButton.setText(i18n.t("sync.keep.remote"));
        entityCol.setText(i18n.t("sync.col.entity"));
        fieldCol.setText(i18n.t("sync.col.field"));
        localCol.setText(i18n.t("sync.col.local"));
        remoteCol.setText(i18n.t("sync.col.remote"));
        updateStatus();
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(service.pendingConflicts()));
        updateStatus();
    }

    private void updateStatus() {
        String last = service.lastSync()
                .map(this::formatDate)
                .orElse(i18n.t("sync.never"));
        statusLabel.setText(i18n.t("sync.status", last, service.unsyncedChangesCount()));

        boolean blocked = service.hasConflicts();
        syncButton.setDisable(blocked);
    }

    @FXML
    private void onSimulate() {
        if (service.simulateRemoteConflict()) {
            reporter.reportWarning(i18n.t("sync.simulated"));
            refresh();
        } else {
            reporter.reportInfo(i18n.t("sync.simulate.empty"));
        }
    }

    @FXML
    private void onSync() {
        if (service.hasConflicts()) {
            reporter.reportWarning(i18n.t("sync.blocked"));
            return;
        }
        app.pluginContext().getEventBus()
                        .publish("sync.start", null);
    }

    @FXML
    private void onKeepLocal() {
        resolveSelected(SyncService.Choice.LOCAL);
    }

    @FXML
    private void onKeepRemote() {
        resolveSelected(SyncService.Choice.REMOTE);
    }

    private void resolveSelected(SyncService.Choice choice) {
        PendingConflict selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            reporter.reportWarning(i18n.t("sync.select.first"));
            return;
        }
        service.resolve(selected, choice);
        reporter.reportInfo(i18n.t("sync.resolved"));
        refresh();
    }

    private String formatDate(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(i18n.locale()))
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
}
