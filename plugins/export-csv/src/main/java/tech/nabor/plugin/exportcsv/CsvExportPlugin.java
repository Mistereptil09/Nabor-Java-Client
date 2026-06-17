package tech.nabor.plugin.exportcsv;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class CsvExportPlugin implements NaborPlugin {

    private PluginContext ctx;
    private ResourceBundle bundle;

    private VBox root;
    private ComboBox<String> entityBox;
    private VBox fieldsBox;
    private Map<String, CheckBox> fieldChecks = new LinkedHashMap<>();

    private static final List<String> ENTITY_TYPES = List.of("incidents", "users", "listings", "events");

    @Override public String getId() { return "export-csv"; }
    @Override public String getDisplayName() { return "CSV Export"; }
    @Override public void shutdown() {}

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        this.bundle = loadBundle();
    }

    @Override
    public Optional<Node> getView() {
        if (root == null) {
            try { buildUi(); } catch (Throwable e) { return Optional.empty(); }
        }
        return Optional.of(root);
    }

    // ── i18n ────────────────────────────────────────────────────────────────

    private ResourceBundle loadBundle() {
        String locale = "fr";
        try { locale = ctx.getI18n().getLocale(); } catch (Exception ignored) {}
        return ResourceBundle.getBundle("i18n/export-csv/messages",
                java.util.Locale.forLanguageTag(locale));
    }
    private String t(String key, Object... args) {
        try { return java.text.MessageFormat.format(bundle.getString(key), args); }
        catch (Exception e) { return key; }
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private void buildUi() {
        Label titleLabel = new Label(t("export.title"));
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        entityBox = new ComboBox<>(FXCollections.observableArrayList(ENTITY_TYPES));
        entityBox.setPromptText(t("export.select"));
        entityBox.setMaxWidth(250);
        entityBox.valueProperty().addListener((o, old, type) -> populateFields(type));

        fieldsBox = new VBox(6);
        ScrollPane scroll = new ScrollPane(fieldsBox);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(250);

        Button selectAll = new Button(t("export.selectAll"));
        selectAll.getStyleClass().add("nav-button");
        selectAll.setOnAction(e -> fieldChecks.values().forEach(c -> c.setSelected(true)));

        Button deselectAll = new Button(t("export.deselectAll"));
        deselectAll.getStyleClass().add("nav-button");
        deselectAll.setOnAction(e -> fieldChecks.values().forEach(c -> c.setSelected(false)));

        HBox selectButtons = new HBox(6, selectAll, deselectAll);

        Button exportBtn = new Button(t("export.button"));
        exportBtn.getStyleClass().add("accent-button");
        exportBtn.setMaxWidth(250);
        exportBtn.setOnAction(e -> doExport());

        root = new VBox(12,
                titleLabel,
                new Label(t("export.entity")), entityBox,
                new Label(t("export.fields")), selectButtons, scroll,
                exportBtn);
        root.setPadding(new Insets(16));
    }

    // ── Field population ────────────────────────────────────────────────────

    private void populateFields(String entityType) {
        fieldsBox.getChildren().clear();
        fieldChecks.clear();
        if (entityType == null) return;

        List<Map<String, Object>> data = loadData(entityType);
        if (data.isEmpty()) return;

        // Use first row to discover field names via reflection
        Object first = data.getFirst().get("_entity");
        if (first == null) return;

        for (var comp : first.getClass().getRecordComponents()) {
            String name = comp.getName();
            CheckBox cb = new CheckBox(name);
            cb.setSelected(true);
            fieldChecks.put(name, cb);
            fieldsBox.getChildren().add(cb);
        }
    }

    // ── Data loading ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadData(String entityType) {
        List<?> entities = switch (entityType) {
            case "incidents" -> ctx.getDb().incidents().findAll();
            case "users"     -> ctx.getDb().users().findAll();
            case "listings"  -> ctx.getDb().listings().findAll();
            case "events"    -> ctx.getDb().evenements().findAll();
            default -> List.of();
        };
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object e : entities) result.add(Map.of("_entity", e));
        return result;
    }

    // ── Export ──────────────────────────────────────────────────────────────

    private void doExport() {
        String type = entityBox.getValue();
        if (type == null) return;

        List<String> selected = fieldChecks.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey).toList();
        if (selected.isEmpty()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV — " + type);
        chooser.setInitialFileName(type + ".csv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;

        List<Map<String, Object>> data = loadData(type);
        try (PrintWriter w = new PrintWriter(file, StandardCharsets.UTF_8)) {
            // Header
            w.println(String.join(",", selected));
            // Rows
            for (var row : data) {
                Object entity = row.get("_entity");
                List<String> values = new ArrayList<>();
                for (String field : selected) {
                    values.add(escapeCsv(getField(entity, field)));
                }
                w.println(String.join(",", values));
            }
        } catch (Exception e) {
            Platform.runLater(() ->
                    ctx.getReporter().reportWarning(t("export.error", e.getMessage())));
            return;
        }
        Platform.runLater(() ->
                ctx.getReporter().reportInfo(t("export.success", file.getName())));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static String getField(Object entity, String fieldName) {
        try {
            var m = entity.getClass().getMethod(fieldName);
            Object val = m.invoke(entity);
            if (val == null) return "";
            if (val instanceof Instant i) return i.toString();
            if (val instanceof Enum<?> e) return e.name();
            return val.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
