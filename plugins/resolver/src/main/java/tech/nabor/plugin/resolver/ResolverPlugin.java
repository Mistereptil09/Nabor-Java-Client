package tech.nabor.plugin.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.api.model.sync.ResolvedConflict;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class ResolverPlugin implements NaborPlugin {

    private PluginContext ctx;
    private final ObjectMapper mapper = new ObjectMapper();
    private ResourceBundle bundle;

    private TableView<PendingConflict> table;
    private Label titleLabel;
    private Label emptyLabel;
    private VBox root;

    @Override public String getId() { return "resolver"; }
    @Override public String getDisplayName() { return "Resolve Conflicts"; }
    @Override public void shutdown() {}

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        this.bundle = loadBundle();

        ctx.getEventBus().subscribe("sync.completed", payload -> refresh());
        ctx.getEventBus().subscribe("sync.push.failed", payload -> refresh());
        refresh();
    }

    private ResourceBundle loadBundle() {
        String locale = "fr";
        try { locale = ctx.getI18n().getLocale(); } catch (Exception ignored) {}
        return ResourceBundle.getBundle("i18n/resolver/messages",
                Locale.forLanguageTag(locale));
    }

    private String t(String key) {
        try { return bundle.getString(key); }
        catch (Exception e) { return key; }
    }

    @Override
    public Optional<Node> getView() {
        if (root == null) {
            try {
                buildUi();
            } catch (Throwable e) {
                // JavaFX toolkit not available (headless/test mode)
                return Optional.empty();
            }
        }
        return Optional.ofNullable(root);
    }

    // ── UI ─────────────────────────────────────────────────────────────────

    private void buildUi() {
        titleLabel = new Label(t("resolver.title"));
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        emptyLabel = new Label(t("resolver.empty"));
        emptyLabel.setStyle("-fx-text-fill: #8C8C8C; -fx-font-size: 14px;");

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<PendingConflict, String> entityCol = col(t("resolver.col.entity"),
                c -> formatTableName(c.tableName()));
        TableColumn<PendingConflict, String> fieldCol = col(t("resolver.col.field"),
                c -> c.fieldName() != null ? c.fieldName() : "(whole record)");
        TableColumn<PendingConflict, String> localCol = col(t("resolver.col.local"),
                ResolverPlugin::summarizeLocal);
        TableColumn<PendingConflict, String> remoteCol = col(t("resolver.col.remote"),
                ResolverPlugin::summarizeRemote);
        TableColumn<PendingConflict, Void> actionsCol = new TableColumn<>(t("resolver.col.actions"));

        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button localBtn = new Button(t("resolver.keep.local"));
            private final Button remoteBtn = new Button(t("resolver.keep.remote"));
            private final HBox box = new HBox(6, localBtn, remoteBtn);

            {
                localBtn.getStyleClass().add("accent-button");
                remoteBtn.getStyleClass().add("nav-button");
                localBtn.setOnAction(e -> {
                    PendingConflict c = currentConflict();
                    if (c != null) resolve(c, Choice.LOCAL);
                });
                remoteBtn.setOnAction(e -> {
                    PendingConflict c = currentConflict();
                    if (c != null) resolve(c, Choice.REMOTE);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(box);
            }

            private PendingConflict currentConflict() {
                int idx = getIndex();
                return idx >= 0 && idx < getTableView().getItems().size()
                        ? getTableView().getItems().get(idx) : null;
            }
        });

        table.getColumns().setAll(entityCol, fieldCol, localCol, remoteCol, actionsCol);

        root = new VBox(10, titleLabel, emptyLabel, table);
        root.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);

        refresh();
    }

    private static <T> TableColumn<PendingConflict, T> col(
            String name, java.util.function.Function<PendingConflict, T> extractor) {
        TableColumn<PendingConflict, T> c = new TableColumn<>(name);
        c.setCellValueFactory(data ->
                new javafx.beans.property.ReadOnlyObjectWrapper<>(
                        extractor.apply(data.getValue())));
        return c;
    }

    // ── Data ────────────────────────────────────────────────────────────────

    private void refresh() {
        List<PendingConflict> conflicts = ctx.getDb().pendingConflicts().findAll();
        if (table == null) return;
        try {
            Platform.runLater(() -> updateTable(conflicts));
        } catch (IllegalStateException e) {
            // JavaFX toolkit not running (headless/test mode) — skip UI update
        }
    }

    private void updateTable(List<PendingConflict> conflicts) {
        table.setItems(FXCollections.observableArrayList(conflicts));
        boolean empty = conflicts.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);
        table.setVisible(!empty);
        table.setManaged(!empty);
    }

    // ── Resolution ──────────────────────────────────────────────────────────

    private enum Choice { LOCAL, REMOTE }

    private void resolve(PendingConflict conflict, Choice choice) {
        if (conflict == null) return;

        try {
            JsonNode serverData = mapper.readTree(conflict.remoteValue());
            String serverUpdatedAt = serverData.path("updatedAt").asText(null);

            if (choice == Choice.LOCAL) {
                // Update outbox base_updated_at so re-push passes conflict check
                updateOutboxBaseUpdatedAt(conflict.tableName(), conflict.rowId(), serverUpdatedAt);
            } else {
                // REMOTE: server data already applied on conflict detection; just clear outbox
                ctx.getDb().syncChangelog().deleteByTableAndRow(
                        conflict.tableName(), conflict.rowId());
            }

            ctx.getDb().pendingConflicts().delete(conflict.id());
            ctx.getDb().resolvedConflicts().save(new ResolvedConflict(
                    0, conflict.tableName(), conflict.rowId(), conflict.fieldName(),
                    choice == Choice.LOCAL ? "local" : "remote", Instant.now()));

            if (choice == Choice.LOCAL) ctx.getEventBus().publish("sync.push", null);
            Platform.runLater(() -> refresh());

        } catch (Exception e) {
            ctx.getReporter().reportWarning("Failed to resolve conflict: " + e.getMessage());
        }
    }

    /** Update outbox base_updated_at so re-push passes server conflict check. */
    private void updateOutboxBaseUpdatedAt(String tableName, String rowId, String newBase) {
        var entries = ctx.getDb().syncChangelog().findByTable(tableName);
        for (var c : entries) {
            if (c.rowId().equals(rowId)) {
                ctx.getDb().syncChangelog().track(new tech.nabor.api.event.ChangeEvent(
                        c.tableName(), c.rowId(), c.operation(),
                        c.previousValues(), c.newValues(), newBase, Instant.now()));
                ctx.getDb().syncChangelog().deleteById(c.id());
                break;
            }
        }
    }

    // ── Display helpers ─────────────────────────────────────────────────────

    private static String formatTableName(String table) {
        return switch (table) {
            case "incidents" -> "Incident";
            case "listings" -> "Listing";
            case "events" -> "Event";
            case "users" -> "User";
            default -> table;
        };
    }

    private static String summarizeLocal(PendingConflict c) {
        try { return summarizeValue(c.localValue(), c.fieldName()); }
        catch (Exception e) { return c.localValue(); }
    }

    private static String summarizeRemote(PendingConflict c) {
        try { return summarizeValue(c.remoteValue(), c.fieldName()); }
        catch (Exception e) { return c.remoteValue(); }
    }

    private static String summarizeValue(String json, String field) {
        if (json == null) return "—";
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            if (field != null && !field.isBlank()) {
                return field + ": " + node.path(field).asText("—");
            }
            // Whole-record: show keys present
            var names = node.fieldNames();
            StringBuilder sb = new StringBuilder();
            while (names.hasNext()) {
                String fn = names.next();
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(fn).append(": ").append(node.path(fn).asText());
            }
            return sb.isEmpty() ? json : sb.toString();
        } catch (Exception e) {
            return json;
        }
    }

    private IncidentSeverity parseSeverity(String s) {
        try { return IncidentSeverity.valueOf(s); }
        catch (Exception e) { return IncidentSeverity.medium; }
    }

    private IncidentStatus parseStatus(String s) {
        try { return IncidentStatus.valueOf(s); }
        catch (Exception e) { return IncidentStatus.open; }
    }
}
