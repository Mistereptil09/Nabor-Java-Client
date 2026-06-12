package tech.nabor.plugin.calendar;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.MappingNeighbourhood;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

public class CalendarPlugin implements NaborPlugin {

    private PluginContext ctx;
    private ResourceBundle bundle;
    private VBox root;
    private Map<String, String> nbNames = Map.of();

    @Override public String getId() { return "plugin-calendar"; }
    @Override public String getDisplayName() { return "Calendar"; }
    @Override public void shutdown() {}

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        this.bundle = loadBundle();
        ctx.getEventBus().subscribe("sync.completed", p -> rebuild());
    }

    @Override public Optional<Node> getView() {
        if (root == null) {
            try { buildUi(); } catch (Throwable e) { return Optional.empty(); }
        }
        return Optional.of(root);
    }

    // ── i18n ────────────────────────────────────────────────────────────────

    private ResourceBundle loadBundle() {
        String locale = "fr";
        try { locale = ctx.getI18n().getLocale(); } catch (Exception ignored) {}
        return ResourceBundle.getBundle("i18n/calendar/messages",
                java.util.Locale.forLanguageTag(locale));
    }
    private String t(String key, Object... args) {
        try { return java.text.MessageFormat.format(bundle.getString(key), args); }
        catch (Exception e) { return key; }
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private TabPane tabs;

    private void buildUi() {
        Label titleLabel = new Label(t("calendar.title"));
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        loadNbNames();
        tabs = new TabPane();
        VBox.setVgrow(tabs, Priority.ALWAYS);

        root = new VBox(12, titleLabel, tabs);
        root.setPadding(new Insets(16));
        rebuild();
    }

    private void rebuild() {
        tabs.getTabs().setAll(buildEventsTab(), buildIncidentsTab());
    }

    private void loadNbNames() {
        nbNames = ctx.getDb().mappingNeighbourhoods().findAll().stream()
                .collect(Collectors.toMap(MappingNeighbourhood::neighbourhoodId,
                        MappingNeighbourhood::neighbourhoodName, (a, b) -> a));
    }

    // ── Events tab ──────────────────────────────────────────────────────────

    private Tab buildEventsTab() {
        List<Evenement> events = ctx.getDb().evenements().findAll().stream()
                .filter(e -> e.startsAt() != null)
                .sorted(Comparator.comparing(Evenement::startsAt))
                .toList();

        TableView<Evenement> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Evenement, String> dateCol = col("calendar.col.date",
                e -> fmt(e.startsAt()), 140);
        TableColumn<Evenement, String> titleCol = col("calendar.col.title",
                Evenement::title, 220);
        TableColumn<Evenement, String> statusCol = col("calendar.col.status",
                e -> e.status().name(), 80);
        TableColumn<Evenement, String> nbCol = col("calendar.col.neighbourhood",
                e -> nbNames.getOrDefault(e.neighbourhoodId(), "—"), 130);
        TableColumn<Evenement, String> partCol = col("calendar.col.participants",
                e -> e.maxParticipants() != null ? String.valueOf(e.maxParticipants()) : "—", 80);

        table.getColumns().addAll(dateCol, titleCol, statusCol, nbCol, partCol);
        table.setItems(FXCollections.observableArrayList(events));

        VBox box = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        if (events.isEmpty()) box.getChildren().add(new Label(t("calendar.empty")));
        box.setPadding(new Insets(8, 0, 0, 0));

        Tab tab = new Tab(t("calendar.tab.events", events.size()), box);
        tab.setClosable(false);
        return tab;
    }

    // ── Incidents tab ───────────────────────────────────────────────────────

    private Tab buildIncidentsTab() {
        List<Incident> incidents = ctx.getDb().incidents().findAll().stream()
                .filter(i -> i.createdAt() != null)
                .sorted(Comparator.comparing(Incident::createdAt).reversed())
                .toList();

        TableView<Incident> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Incident, String> dateCol = col("calendar.col.date",
                i -> fmt(i.createdAt()), 140);
        TableColumn<Incident, String> titleCol = col("calendar.col.title",
                Incident::title, 220);
        TableColumn<Incident, String> statusCol = col("calendar.col.status",
                i -> i.status().name(), 80);
        TableColumn<Incident, String> sevCol = col("calendar.col.severity",
                i -> i.severity().name(), 80);
        TableColumn<Incident, String> nbCol = col("calendar.col.neighbourhood",
                i -> nbNames.getOrDefault(i.neighbourhoodId(), "—"), 130);

        table.getColumns().addAll(dateCol, titleCol, statusCol, sevCol, nbCol);
        table.setItems(FXCollections.observableArrayList(incidents));

        VBox box = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        if (incidents.isEmpty()) box.getChildren().add(new Label(t("calendar.empty")));
        box.setPadding(new Insets(8, 0, 0, 0));

        Tab tab = new Tab(t("calendar.tab.incidents", incidents.size()), box);
        tab.setClosable(false);
        return tab;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private <T> TableColumn<T, String> col(String key,
                                            java.util.function.Function<T, String> fn, double w) {
        TableColumn<T, String> c = new TableColumn<>(t(key));
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(fn.apply(d.getValue())));
        return c;
    }

    private String fmt(Instant i) {
        if (i == null) return "—";
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(java.util.Locale.forLanguageTag(bundle.getLocale().getLanguage()))
                .withZone(ZoneId.systemDefault()).format(i);
    }
}
