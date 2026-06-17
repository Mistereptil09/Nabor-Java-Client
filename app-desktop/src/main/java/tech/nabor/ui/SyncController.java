package tech.nabor.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import tech.nabor.AppContext;
import tech.nabor.api.EventBus;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.service.SyncService;
import tech.nabor.ui.i18n.I18nManager;

/**
 * Sync status screen — shows last sync time, unsynced count, and a sync button.
 * Conflict resolution has moved to the resolver plugin ({@code ResolverPlugin}).
 */
public class SyncController {

    @FXML private Label screenTitle;
    @FXML private Label statusLabel;
    @FXML private Label lastSyncLabel;
    @FXML private Label conflictsLabel;
    @FXML private Button syncButton;

    private SyncService service;
    private I18nManager i18n;
    private NaborReporter reporter;
    private AppContext app;

    public void init(AppContext app, I18nManager i18n) {
        this.app = app;
        this.i18n = i18n;
        this.service = new SyncService(app.pluginContext());
        this.reporter = app.pluginContext().getReporter();

        EventBus eventBus = app.pluginContext().getEventBus();
        eventBus.subscribe(UiEvents.SYNC_CHANGED, payload -> Platform.runLater(this::refresh));
        eventBus.subscribe("sync.completed", payload -> Platform.runLater(this::refresh));
        eventBus.subscribe("sync.failed", payload -> Platform.runLater(() ->
                reporter.reportWarning("Sync failed: " + payload)));

        i18n.onLocaleChange(this::applyTexts);
        applyTexts();
        refresh();
    }

    private void applyTexts() {
        screenTitle.setText(i18n.t("sync.title"));
        syncButton.setText(i18n.t("sync.now"));
        updateStatus();
    }

    private void refresh() {
        updateStatus();
    }

    private void updateStatus() {
        String last = service.lastSync()
                .map(this::formatDate)
                .orElse(i18n.t("sync.never"));
        statusLabel.setText(i18n.t("sync.status", last, service.unsyncedChangesCount()));

        boolean blocked = service.hasConflicts();
        syncButton.setDisable(blocked);
        if (blocked) {
            conflictsLabel.setText(i18n.t("sync.blocked"));
            conflictsLabel.setVisible(true);
        } else {
            conflictsLabel.setVisible(false);
        }
    }

    @FXML
    private void onSync() {
        if (service.hasConflicts()) {
            reporter.reportWarning(i18n.t("sync.blocked"));
            return;
        }
        reporter.reportInfo(i18n.t("sync.started"));
        app.pluginContext().getEventBus().publish("sync.start", null);
    }

    private String formatDate(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(i18n.locale()))
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
}
