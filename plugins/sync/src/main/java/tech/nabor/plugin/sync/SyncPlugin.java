package tech.nabor.plugin.sync;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.sync.SyncState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SyncPlugin implements NaborPlugin {

    private PluginContext ctx;
    private PullEngine pullEngine;
    private PushEngine pushEngine;
    private ResourceBundle bundle;

    // UI
    private VBox root;
    private Label statusLabel;
    private TextField sinceField;

    @Override public String getId() { return "sync"; }
    @Override public String getDisplayName() { return "Sync"; }
    @Override public void shutdown() {}

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        this.pullEngine = new PullEngine(ctx);
        this.pushEngine = new PushEngine(ctx);
        this.bundle = loadBundle();

        ctx.getEventBus().subscribe("sync.start",  p -> new Thread(this::doPull).start());
        ctx.getEventBus().subscribe("sync.full",   p -> new Thread(this::doPullFromEpoch).start());
        ctx.getEventBus().subscribe("sync.push",   p -> new Thread(this::doPush).start());
        ctx.getEventBus().subscribe("sync.completed", p -> refreshView());
        ctx.getEventBus().subscribe("sync.push.completed", p -> refreshView());
        ctx.getEventBus().subscribe("sync.push.failed",    p -> refreshView());
    }

    @Override
    public Optional<Node> getView() {
        if (root == null) {
            try { buildUi(); } catch (Throwable e) { return Optional.empty(); }
        }
        return Optional.ofNullable(root);
    }

    // ── i18n ────────────────────────────────────────────────────────────────

    private ResourceBundle loadBundle() {
        String locale = "fr";
        try { locale = ctx.getI18n().getLocale(); } catch (Exception ignored) {}
        return ResourceBundle.getBundle("i18n/sync/messages",
                java.util.Locale.forLanguageTag(locale));
    }

    private String t(String key, Object... args) {
        try { return java.text.MessageFormat.format(bundle.getString(key), args); }
        catch (Exception e) { return key; }
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private void buildUi() {
        Label titleLabel = new Label(t("sync.title"));
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 13px;");

        sinceField = new TextField();
        sinceField.setPromptText("since (ISO date, optional)");
        sinceField.setStyle("-fx-max-width: 260px;");

        Button pullBtn = new Button(t("sync.now"));
        pullBtn.getStyleClass().add("accent-button");
        pullBtn.setOnAction(e -> ctx.getEventBus().publish("sync.start", null));

        Button pullAllBtn = new Button(t("sync.all"));
        pullAllBtn.getStyleClass().add("nav-button");
        pullAllBtn.setOnAction(e -> ctx.getEventBus().publish("sync.full", null));

        Button pushBtn = new Button(t("sync.push"));
        pushBtn.getStyleClass().add("nav-button");
        pushBtn.setOnAction(e -> ctx.getEventBus().publish("sync.push", null));

        HBox buttons = new HBox(10, pullBtn, pullAllBtn, pushBtn);
        root = new VBox(12, titleLabel, statusLabel, sinceField, buttons);
        root.setPadding(new Insets(16));
        refreshView();
    }

    private void refreshView() {
        if (statusLabel == null) return;
        try {
            Platform.runLater(() -> {
                var state = ctx.getDb().syncState().get();
                int pending = ctx.getDb().syncChangelog().findAll().size();
                int conflicts = ctx.getDb().pendingConflicts().findAll().size();

                String lastStr = state.map(SyncState::latestSyncCursor)
                        .filter(c -> c != null && !c.isBlank())
                        .map(c -> formatCursorTime(c))
                        .orElse(t("sync.never"));
                statusLabel.setText(t("sync.status", lastStr) + "\n"
                        + t("sync.pending", pending));
                if (conflicts > 0) {
                    statusLabel.setText(statusLabel.getText() + "\n"
                            + t("sync.conflicts", conflicts));
                }
            });
        } catch (IllegalStateException ignored) {}
    }

    // ── Pull ────────────────────────────────────────────────────────────────

    private void doPull() {
        if (!canPull()) return;
        try {
            String since = sinceField != null ? sinceField.getText() : null;
            if (since != null && !since.isBlank()) {
                pullEngine.pullSince(since);
            } else {
                pullEngine.pull();
            }
            ctx.getEventBus().publish("sync.completed", null);
        } catch (Exception e) {
            System.out.println("[Sync] Pull failed: " + e.getMessage());
            ctx.getEventBus().publish("sync.failed", e.getMessage());
        }
    }

    private void doPullFromEpoch() {
        if (!canPull()) return;
        try {
            pullEngine.pullFromEpoch();
            ctx.getEventBus().publish("sync.completed", null);
        } catch (Exception e) {
            System.out.println("[Sync] Pull-from-epoch failed: " + e.getMessage());
            ctx.getEventBus().publish("sync.failed", e.getMessage());
        }
    }

    // ── Push ────────────────────────────────────────────────────────────────

    private void doPush() {
        try {
            boolean allApplied = pushEngine.push();
            if (allApplied) {
                pullEngine.pull();
                ctx.getEventBus().publish("sync.completed", null);
            }
        } catch (Exception e) {
            System.out.println("[Sync] Push failed: " + e.getMessage());
            ctx.getEventBus().publish("sync.push.failed", e.getMessage());
        }
    }

    // ── Confirmation dialog ─────────────────────────────────────────────────

    private boolean canPull() {
        if (ctx.getConnectedUser() == null) return true; // headless/test
        String userId = ctx.getConnectedUser().getUserId();
        var ack = ctx.getDb().pluginConfigs().getValue(userId, "sync", "pull.warning.acknowledged");
        if (ack.isPresent() && "true".equals(ack.get())) return true;

        CompletableFuture<Boolean> choice = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, t("sync.pull.warning"),
                    ButtonType.YES, ButtonType.NO);
            alert.setTitle(t("sync.pull.warning.title"));
            alert.setHeaderText(t("sync.pull.warning.header"));

            CheckBox dontShow = new CheckBox(t("sync.pull.warning.dontShow"));
            alert.getDialogPane().setExpandableContent(dontShow);
            alert.getDialogPane().setExpanded(true);

            var result = alert.showAndWait();
            boolean accepted = result.isPresent() && result.get() == ButtonType.YES;
            if (accepted && dontShow.isSelected()) {
                ctx.getDb().pluginConfigs().setValue(userId, "sync",
                        "pull.warning.acknowledged", "true");
            }
            choice.complete(accepted);
        });

        try { return choice.get(); } catch (Exception e) { return false; }
    }

    private String formatCursorTime(String cursor) {
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(cursor));
            String iso = decoded.split("\\|")[0]; // "2026-06-02T23:56:56.485Z"
            Instant instant = Instant.parse(iso);
            return java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                            java.time.format.FormatStyle.SHORT)
                    .withLocale(java.util.Locale.forLanguageTag(bundle.getLocale().getLanguage()))
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(instant);
        } catch (Exception e) {
            return t("sync.synced"); // fallback if cursor is malformed
        }
    }
}
