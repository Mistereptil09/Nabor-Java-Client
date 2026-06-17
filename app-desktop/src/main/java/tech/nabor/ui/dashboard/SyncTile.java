package tech.nabor.ui.dashboard;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.sync.SyncState;

/** Quick sync status + Sync Now button. */
public class SyncTile extends DashboardTile {

    private Label statusLabel;
    private PluginContext ctx;

    @Override public String getId() { return "sync-quick"; }
    @Override public String getTitle() { return "Sync Status"; }
    @Override public int getColSpan() { return 1; }

    @Override
    public Node build(PluginContext context) {
        this.ctx = context;
        statusLabel = new Label();
        statusLabel.getStyleClass().add("kpi-label");

        Button syncBtn = new Button("Sync Now");
        syncBtn.getStyleClass().add("accent-button");
        syncBtn.setOnAction(e -> ctx.getEventBus().publish("sync.start", null));

        VBox box = new VBox(10, statusLabel, syncBtn);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("kpi-card");
        refreshStatus();
        return box;
    }

    private void refreshStatus() {
        if (statusLabel == null || ctx == null) return;
        var state = ctx.getDb().syncState().get();
        String lastSync = state.map(SyncState::latestSyncCursor)
                .filter(c -> c != null && !c.isBlank())
                .map(this::decodeCursorTime)
                .orElse("Not synced");

        int pending = ctx.getDb().syncChangelog().findAll().size();
        int conflicts = ctx.getDb().pendingConflicts().findAll().size();
        String text = "Last sync: " + lastSync
                + "\nPending: " + pending
                + "\nConflicts: " + conflicts;
        statusLabel.setText(text);
    }

    private String decodeCursorTime(String cursor) {
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(cursor));
            String iso = decoded.split("\\|")[0];
            java.time.Instant i = java.time.Instant.parse(iso);
            return java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                    java.time.format.FormatStyle.SHORT)
                    .withZone(java.time.ZoneId.systemDefault()).format(i);
        } catch (Exception e) { return "Synced"; }
    }

    @Override public void refresh() { refreshStatus(); }
}
