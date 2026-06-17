package tech.nabor.ui.dashboard;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tech.nabor.AppContext;
import tech.nabor.api.PluginContext;
import tech.nabor.ui.i18n.I18nManager;

import java.util.*;
import java.util.function.Supplier;

public class DashboardController {

    private AppContext app;
    private PluginContext ctx;
    private I18nManager i18n;

    private GridPane grid;
    private List<TilePlacement> layout;
    private Map<String, Supplier<DashboardTile>> factories = new LinkedHashMap<>();

    private Node draggingTile;
    private Node highlightTarget;

    private static final int COLS = 4;

    public void init(AppContext app, I18nManager i18n) {
        this.app = app;
        this.i18n = i18n;
        this.ctx = app.pluginContext();

        factories.put("kpi-incidents",  () -> new KpiTile("kpi-incidents",
                "Incidents",
                db -> String.valueOf(db.incidents().findAll().size()),
                db -> {
                    int open = db.incidents().findByStatus(tech.nabor.api.model.enums.IncidentStatus.open, 1000).size()
                            + db.incidents().findByStatus(tech.nabor.api.model.enums.IncidentStatus.in_progress, 1000).size();
                    int resolved = db.incidents().findByStatus(tech.nabor.api.model.enums.IncidentStatus.resolved, 1000).size();
                    return open + " open · " + resolved + " resolved";
                }));
        factories.put("kpi-events",    () -> new KpiTile("kpi-events",
                "Events",
                db -> String.valueOf(db.evenements().findAll().stream()
                        .filter(e -> e.deletedAt() == null).count()),
                db -> {
                    long upcoming = db.evenements().findAll().stream()
                            .filter(e -> e.startsAt() != null && e.startsAt().isAfter(java.time.Instant.now())
                                    && e.deletedAt() == null).count();
                    return upcoming + " upcoming";
                }));
        factories.put("kpi-open",      () -> new KpiTile("kpi-open",
                "Open Incidents", db -> String.valueOf(
                        db.incidents().findByStatus(tech.nabor.api.model.enums.IncidentStatus.open, 1000).size()
                        + db.incidents().findByStatus(tech.nabor.api.model.enums.IncidentStatus.in_progress, 1000).size())));
        factories.put("kpi-pending",   () -> new KpiTile("kpi-pending",
                "Pending Changes", db -> String.valueOf(db.syncChangelog().findAll().size())));
        factories.put("kpi-conflicts", () -> new KpiTile("kpi-conflicts",
                "Conflicts", db -> String.valueOf(db.pendingConflicts().findAll().size())));
        factories.put("chart-status",  StatusChartTile::new);
        factories.put("chart-trend",   TrendChartTile::new);
        factories.put("chart-severity",SeverityChartTile::new);
        factories.put("table-recent",  RecentIncidentsTile::new);
        factories.put("sync-quick",    SyncTile::new);

        layout = DashboardLayout.load(ctx);

        ctx.getEventBus().subscribe("sync.completed", p -> Platform.runLater(this::rebuildGrid));
        ctx.getEventBus().subscribe("ui.incidents.changed", p -> Platform.runLater(this::rebuildGrid));
    }

    public Node getView() {
        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        for (int i = 0; i < COLS; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / COLS);
            grid.getColumnConstraints().add(cc);
        }
        rebuildGrid();

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-area");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        MenuButton addBtn = new MenuButton("+ Add Tile");
        addBtn.getStyleClass().add("accent-button");
        addBtn.setOnShowing(e -> refreshAddMenu(addBtn));

        HBox toolbar = new HBox(8, addBtn);
        toolbar.setPadding(new Insets(0, 12, 0, 12));
        VBox root = new VBox(8, toolbar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return root;
    }

    // ── Grid ───────────────────────────────────────────────────────────────

    private void rebuildGrid() {
        grid.getChildren().clear();
        for (TilePlacement tp : layout) {
            var f = factories.get(tp.tileId());
            if (f == null) continue;
            DashboardTile tile = f.get();

            VBox wrapper = new VBox(0);
            wrapper.getStyleClass().add("dashboard-tile");

            HBox header = new HBox(4);
            header.getStyleClass().add("tile-header");
            Label title = new Label(tile.getTitle());
            HBox.setHgrow(title, Priority.ALWAYS);
            Button closeBtn = new Button("✕");
            closeBtn.getStyleClass().add("nav-button");
            closeBtn.setOnAction(e -> removeTile(tp.tileId()));
            header.getChildren().addAll(title, closeBtn);
            header.setOnMousePressed(e -> startDrag(wrapper));
            header.setOnMouseDragged(e -> highlightUnderCursor(e.getScreenX(), e.getScreenY()));
            header.setOnMouseReleased(e -> endDrag(e.getScreenX(), e.getScreenY(), tp));

            Node content = tile.build(ctx);
            content.getStyleClass().add("tile-content");
            wrapper.getChildren().addAll(header, content);

            grid.add(wrapper, tp.col(), tp.row(),
                    Math.min(tp.colSpan(), COLS - tp.col()), 1);
        }
    }

    // ── Add / remove ───────────────────────────────────────────────────────

    private void addTile(String tileId) {
        if (layout.stream().anyMatch(tp -> tp.tileId().equals(tileId))) return;
        int span = factories.containsKey(tileId) ? factories.get(tileId).get().getColSpan() : 1;
        layout = new ArrayList<>(layout);
        int maxRow = layout.stream().mapToInt(TilePlacement::row).max().orElse(-1);
        layout.add(new TilePlacement(tileId, 0, maxRow + 1, span));
        compactAndSave();
    }

    private void removeTile(String tileId) {
        layout = new ArrayList<>(layout);
        layout.removeIf(tp -> tp.tileId().equals(tileId));
        compactAndSave();
    }

    // ── Drag & drop ────────────────────────────────────────────────────────

    private void startDrag(Node tile) {
        draggingTile = tile;
        tile.setStyle("-fx-border-color: -nabor-accent; -fx-border-width: 3; -fx-border-radius: 10;");
    }

    private void highlightUnderCursor(double sx, double sy) {
        clearHighlights();
        for (Node child : grid.getChildren()) {
            if (child instanceof VBox w && w.getStyleClass().contains("dashboard-tile")) {
                if (w.localToScreen(w.getBoundsInLocal()).contains(sx, sy)) {
                    highlightTarget = w;
                    w.setStyle("-fx-border-color: derive(-nabor-accent, 30%);"
                            + " -fx-border-width: 2; -fx-border-radius: 10;");
                    return;
                }
            }
        }
    }

    private void clearHighlights() {
        if (draggingTile != null) draggingTile.setStyle("");
        if (highlightTarget != null) { highlightTarget.setStyle(""); highlightTarget = null; }
    }

    private void endDrag(double sx, double sy, TilePlacement source) {
        clearHighlights();
        draggingTile = null;

        for (Node child : grid.getChildren()) {
            if (child instanceof VBox w && w.getStyleClass().contains("dashboard-tile")) {
                if (w.localToScreen(w.getBoundsInLocal()).contains(sx, sy)) {
                    Integer c = GridPane.getColumnIndex(w), r = GridPane.getRowIndex(w);
                    if (c != null && r != null) {
                        swapWith(source, c, r);
                        return;
                    }
                }
            }
        }
    }

    private void swapWith(TilePlacement source, int targetCol, int targetRow) {
        for (int i = 0; i < layout.size(); i++) {
            TilePlacement tp = layout.get(i);
            if (tp.col() == targetCol && tp.row() == targetRow
                    && !tp.tileId().equals(source.tileId())) {
                layout = new ArrayList<>(layout);
                int si = indexOf(source.tileId());
                layout.set(i, new TilePlacement(source.tileId(), targetCol, targetRow, source.colSpan()));
                layout.set(si, new TilePlacement(tp.tileId(), source.col(), source.row(), tp.colSpan()));
                compactAndSave();
                return;
            }
        }
    }

    private void refreshAddMenu(MenuButton menu) {
        menu.getItems().clear();
        List<String> available = factories.keySet().stream()
                .filter(id -> layout.stream().noneMatch(tp -> tp.tileId().equals(id)))
                .sorted().toList();
        if (available.isEmpty()) {
            MenuItem empty = new MenuItem("(no more tiles)");
            empty.setDisable(true);
            menu.getItems().add(empty);
            return;
        }
        for (String id : available) {
            MenuItem item = new MenuItem(id);
            item.setOnAction(e -> addTile(id));
            menu.getItems().add(item);
        }
    }

    // ── Layout ─────────────────────────────────────────────────────────────

    /** Flow-layout: sorts by (row,col) then places each tile in the first free slot. */
    private void compactLayout() {
        layout.sort((a, b) -> a.row() != b.row()
                ? Integer.compare(a.row(), b.row()) : Integer.compare(a.col(), b.col()));

        List<TilePlacement> packed = new ArrayList<>();
        int rows = Math.max(50, layout.size() + 2);
        boolean[][] occ = new boolean[rows][COLS];

        for (TilePlacement tp : layout) {
            boolean placed = false;
            for (int r = 0; r < rows && !placed; r++) {
                for (int c = 0; c <= COLS - tp.colSpan() && !placed; c++) {
                    boolean free = true;
                    for (int k = 0; k < tp.colSpan(); k++)
                        if (occ[r][c + k]) { free = false; break; }
                    if (free) {
                        packed.add(new TilePlacement(tp.tileId(), c, r, tp.colSpan()));
                        for (int k = 0; k < tp.colSpan(); k++) occ[r][c + k] = true;
                        placed = true;
                    }
                }
            }
        }
        layout = packed;
    }

    private void compactAndSave() {
        compactLayout();
        DashboardLayout.save(ctx, layout);
        rebuildGrid();
    }

    private int indexOf(String tileId) {
        for (int i = 0; i < layout.size(); i++)
            if (layout.get(i).tileId().equals(tileId)) return i;
        return -1;
    }
}
