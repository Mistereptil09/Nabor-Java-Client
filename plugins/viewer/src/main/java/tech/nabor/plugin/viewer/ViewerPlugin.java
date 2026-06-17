package tech.nabor.plugin.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;
import tech.nabor.api.event.ChangeEvent;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.MappingNeighbourhood;
import tech.nabor.api.model.sync.SyncChange;
import tech.nabor.api.model.sync.SyncWhitelist;
import tech.nabor.api.model.user.User;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ViewerPlugin implements NaborPlugin {

    private PluginContext ctx;
    private final ObjectMapper mapper = new ObjectMapper();
    private ResourceBundle bundle;

    private TabPane tabPane;
    private VBox root;
    private Map<String, Set<String>> whitelist = new HashMap<>();
    private Map<String, String> neighbourhoodNames = Map.of(); // id → name

    @Override public String getId() { return "viewer"; }
    @Override public String getDisplayName() { return "Data Viewer"; }
    @Override public void shutdown() {}

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        this.bundle = loadBundle();
        new Thread(() -> {
            try { fetchAndStoreWhitelist(); } catch (Exception ignored) {}
            loadWhitelistFromDb();
            Platform.runLater(this::refreshAllTabs);
        }).start();
        ctx.getEventBus().subscribe("sync.completed", p -> refreshAllTabs());
        ctx.getEventBus().subscribe("sync.push.completed", p -> refreshAllTabs());
        ctx.getEventBus().subscribe("ui.incidents.changed", p -> refreshAllTabs());
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
        return ResourceBundle.getBundle("i18n/viewer/messages",
                java.util.Locale.forLanguageTag(locale));
    }
    private String t(String key, Object... args) {
        try { return java.text.MessageFormat.format(bundle.getString(key), args); }
        catch (Exception e) { return key; }
    }

    // ── Whitelist ───────────────────────────────────────────────────────────

    private void fetchAndStoreWhitelist() throws Exception {
        String json = ctx.getHttpClient().get("/sync/whitelist");
        JsonNode root = mapper.readTree(json);
        JsonNode whitelists = root.path("whitelists");
        if (whitelists.isObject()) {
            var fields = whitelists.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                List<String> names = new ArrayList<>();
                JsonNode arr = entry.getValue();
                if (arr.isArray()) for (JsonNode f : arr) names.add(f.asText());
                ctx.getDb().syncWhitelist().replaceAll(entry.getKey(), names);
            }
        }
    }

    private void loadWhitelistFromDb() {
        whitelist.clear();
        for (SyncWhitelist sw : ctx.getDb().syncWhitelist().findAll())
            whitelist.computeIfAbsent(sw.entityType(), k -> new HashSet<>()).add(sw.fieldName());
    }

    private Set<String> whitelistedFields(String entityType) {
        return whitelist.getOrDefault(entityType, Set.of());
    }

    /** rowId → set of changed field names from the outbox */
    private Map<String, Set<String>> dirtyFields(String tableName) {
        return ctx.getDb().syncChangelog().findByTable(tableName).stream()
                .filter(c -> c.changedFields() != null)
                .collect(Collectors.toMap(
                        SyncChange::rowId,
                        c -> new HashSet<>(c.changedFields()),
                        (a, b) -> { a.addAll(b); return a; }));
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private void buildUi() {
        tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        root = new VBox(8, tabPane);
        root.setPadding(new Insets(12));
        // Load viewer CSS (uses -nabor-accent from app theme)
        root.getStylesheets().add(getClass().getResource("/css/viewer.css").toExternalForm());
        buildAllTabs();
    }

    private void loadNeighbourhoodNames() {
        neighbourhoodNames = ctx.getDb().mappingNeighbourhoods().findAll().stream()
                .collect(Collectors.toMap(MappingNeighbourhood::neighbourhoodId,
                        MappingNeighbourhood::neighbourhoodName, (a, b) -> a));
    }

    private void buildAllTabs() {
        this.bundle = loadBundle(); // reload for current locale
        loadNeighbourhoodNames();
        tabPane.getTabs().setAll(
                buildEntityTab("incidents", "incident",
                        () -> ctx.getDb().incidents().findAll(),
                        this::incidentColumns,
                        Incident::id, i -> i.id() + i.title() + i.description()
                                + i.severity().name() + i.status().name()
                                + i.reporterId() + i.assignedTo()
                                + resolveNb(i.neighbourhoodId())
                                + fmt(i.createdAt()) + fmt(i.updatedAt()),
                        incidentExtractors()),
                buildEntityTab("users", "user",
                        () -> ctx.getDb().users().findAll(),
                        this::userColumns,
                        User::id, u -> u.id() + u.firstName() + u.lastName()
                                + u.email() + (u.bio() != null ? u.bio() : "")
                                + u.role().name() + resolveNb(u.neighbourhoodId()),
                        userExtractors()),
                buildEntityTab("listings", "listing",
                        () -> ctx.getDb().listings().findAll(),
                        this::listingColumns,
                        tech.nabor.api.model.listings.Listing::id,
                        l -> l.id() + l.title() + l.description()
                                + l.listingType().name() + l.priceCents()
                                + l.status().name() + l.creatorId(),
                        listingExtractors()),
                buildEntityTab("events", "event",
                        () -> ctx.getDb().evenements().findAll(),
                        this::eventColumns,
                        tech.nabor.api.model.events.Evenement::id,
                        e -> e.id() + e.title() + e.status().name()
                                + e.creatorId() + fmt(e.startsAt()) + fmt(e.endsAt())
                                + (e.maxParticipants() != null ? e.maxParticipants() : ""),
                        eventExtractors()),
                buildNeighbourhoodTab(),
                buildHistoryTab()
        );
    }

    private void refreshAllTabs() {
        try {
            Platform.runLater(() -> {
                int idx = tabPane.getSelectionModel().getSelectedIndex();
                buildAllTabs();
                if (idx >= 0 && idx < tabPane.getTabs().size())
                    tabPane.getSelectionModel().select(idx);
            });
        } catch (IllegalStateException ignored) {}
    }

    // ── Generic entity tab ──────────────────────────────────────────────────

    @FunctionalInterface
    interface ColumnBuilder<T> {
        List<TableColumn<T, String>> build(Set<String> whitelist,
                Map<String, Set<String>> dirtyMap, Function<T, String> idFn);
    }

    private <T> Tab buildEntityTab(String tableName, String entityType,
                                   java.util.function.Supplier<List<T>> loader,
                                   ColumnBuilder<T> columnsFn,
                                   Function<T, String> idFn,
                                   Function<T, String> searchFn,
                                   Map<String, Function<T, String>> columnExtractors) {
        List<T> data = loader.get();
        Set<String> wl = whitelistedFields(entityType);
        Map<String, Set<String>> dirtyMap = dirtyFields(tableName);

        TableView<T> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
                table.getColumns().addAll(columnsFn.build(wl, dirtyMap, idFn));

        // Edit column (pencil icon)
        if (!wl.isEmpty()) {
            TableColumn<T, Void> editCol = new TableColumn<>("Edit");
            editCol.setPrefWidth(60);
            editCol.setCellFactory(col -> new TableCell<>() {
                private final Button btn = new Button("Edit");
                {
                    btn.getStyleClass().add("nav-button");
                    btn.setMaxHeight(20);
                    btn.setOnAction(e -> {
                        T item = getTableView().getItems().get(getIndex());
                        showEditDialog(tableName, entityType, item, idFn.apply(item), wl);
                    });
                }
                @Override protected void updateItem(Void v, boolean empty) {
                    super.updateItem(v, empty);
                    setGraphic(empty ? null : btn);
                }
            });
            table.getColumns().add(editCol);
        }

        return wrapTab(t("viewer.tab." + tableName), table, data, searchFn, columnExtractors);
    }

    // ── Column definitions ──────────────────────────────────────────────────

    private List<TableColumn<Incident, String>> incidentColumns(Set<String> wl,
            Map<String, Set<String>> dm, Function<Incident, String> idFn) {
        return List.of(
                col("ID", Incident::id, 120, null, null, null),
                col("Title", Incident::title, 200, "title", dm, idFn),
                col("Description", Incident::description, 200, "description", dm, idFn),
                col("Severity", i -> i.severity().name(), 80, "severity", dm, idFn),
                col("Status", i -> i.status().name(), 80, "status", dm, idFn),
                col("Reporter", Incident::reporterId, 120, null, null, null),
                col("Assigned", Incident::assignedTo, 120, null, null, null),
                col("Neighbourhood", i -> resolveNb(i.neighbourhoodId()), 120, null, null, null),
                col("Created", i -> fmt(i.createdAt()), 120, null, null, null),
                col("Updated", i -> fmt(i.updatedAt()), 120, null, null, null));
    }

    private List<TableColumn<User, String>> userColumns(Set<String> wl,
            Map<String, Set<String>> dm, Function<User, String> idFn) {
        return List.of(
                col("ID", User::id, 120, null, null, null),
                col("First Name", User::firstName, 120, "firstName", dm, idFn),
                col("Last Name", User::lastName, 120, "lastName", dm, idFn),
                col("Email", User::email, 180, null, null, null),
                col("Bio", u -> u.bio() != null ? u.bio() : "", 150, "bio", dm, idFn),
                col("Role", u -> u.role().name(), 80, null, null, null),
                col("Neighbourhood", u -> resolveNb(u.neighbourhoodId()), 120, null, null, null));
    }

    private List<TableColumn<tech.nabor.api.model.listings.Listing, String>> listingColumns(
            Set<String> wl, Map<String, Set<String>> dm,
            Function<tech.nabor.api.model.listings.Listing, String> idFn) {
        return List.of(
                col("ID", tech.nabor.api.model.listings.Listing::id, 120, null, null, null),
                col("Title", tech.nabor.api.model.listings.Listing::title, 200, "title", dm, idFn),
                col("Description", tech.nabor.api.model.listings.Listing::description, 200, "description", dm, idFn),
                col("Type", l -> l.listingType().name(), 60, null, null, null),
                col("Price", l -> String.valueOf(l.priceCents()), 60, null, null, null),
                col("Status", l -> l.status().name(), 80, "status", dm, idFn),
                col("Creator", tech.nabor.api.model.listings.Listing::creatorId, 120, null, null, null));
    }

    private List<TableColumn<tech.nabor.api.model.events.Evenement, String>> eventColumns(
            Set<String> wl, Map<String, Set<String>> dm,
            Function<tech.nabor.api.model.events.Evenement, String> idFn) {
        return List.of(
                col("ID", tech.nabor.api.model.events.Evenement::id, 120, null, null, null),
                col("Title", tech.nabor.api.model.events.Evenement::title, 200, "title", dm, idFn),
                col("Status", e -> e.status().name(), 80, "status", dm, idFn),
                col("Creator", tech.nabor.api.model.events.Evenement::creatorId, 120, null, null, null),
                col("Starts", e -> fmt(e.startsAt()), 120, null, null, null),
                col("Ends", e -> fmt(e.endsAt()), 120, null, null, null),
                col("Max", e -> e.maxParticipants() != null ? String.valueOf(e.maxParticipants()) : "—", 60, null, null, null));
    }

    // ── Column extractors for filtering ──────────────────────────────────────

    private Map<String, Function<Incident, String>> incidentExtractors() {
        Map<String, Function<Incident, String>> m = new LinkedHashMap<>();
        m.put("ID", Incident::id);
        m.put("Title", Incident::title);
        m.put("Description", Incident::description);
        m.put("Severity", i -> i.severity().name());
        m.put("Status", i -> i.status().name());
        m.put("Reporter", Incident::reporterId);
        m.put("Assigned", Incident::assignedTo);
        m.put("Neighbourhood", i -> resolveNb(i.neighbourhoodId()));
        m.put("Created", i -> fmt(i.createdAt()));
        m.put("Updated", i -> fmt(i.updatedAt()));
        return m;
    }

    private Map<String, Function<User, String>> userExtractors() {
        Map<String, Function<User, String>> m = new LinkedHashMap<>();
        m.put("ID", User::id);
        m.put("First Name", User::firstName);
        m.put("Last Name", User::lastName);
        m.put("Email", User::email);
        m.put("Bio", u -> u.bio() != null ? u.bio() : "");
        m.put("Role", u -> u.role().name());
        m.put("Neighbourhood", u -> resolveNb(u.neighbourhoodId()));
        return m;
    }

    private Map<String, Function<tech.nabor.api.model.listings.Listing, String>> listingExtractors() {
        Map<String, Function<tech.nabor.api.model.listings.Listing, String>> m = new LinkedHashMap<>();
        m.put("ID", tech.nabor.api.model.listings.Listing::id);
        m.put("Title", tech.nabor.api.model.listings.Listing::title);
        m.put("Description", tech.nabor.api.model.listings.Listing::description);
        m.put("Type", l -> l.listingType().name());
        m.put("Price", l -> String.valueOf(l.priceCents()));
        m.put("Status", l -> l.status().name());
        m.put("Creator", tech.nabor.api.model.listings.Listing::creatorId);
        return m;
    }

    private Map<String, Function<tech.nabor.api.model.events.Evenement, String>> eventExtractors() {
        Map<String, Function<tech.nabor.api.model.events.Evenement, String>> m = new LinkedHashMap<>();
        m.put("ID", tech.nabor.api.model.events.Evenement::id);
        m.put("Title", tech.nabor.api.model.events.Evenement::title);
        m.put("Status", e -> e.status().name());
        m.put("Creator", tech.nabor.api.model.events.Evenement::creatorId);
        m.put("Starts", e -> fmt(e.startsAt()));
        m.put("Ends", e -> fmt(e.endsAt()));
        m.put("Max", e -> e.maxParticipants() != null ? String.valueOf(e.maxParticipants()) : "—");
        return m;
    }

    // ── Edit dialog ─────────────────────────────────────────────────────────

    private <T> void showEditDialog(String tableName, String entityType,
                                     T entity, String rowId, Set<String> fields) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle(t("viewer.edit.title") + " — " + rowId);
        dialog.setHeaderText(entityType + " — " + t("viewer.edit.whitelist"));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Style dialog: left accent border using looked-up color
        dialog.getDialogPane().setStyle(
            "-fx-border-width: 0 0 0 4; -fx-border-color: #F7931E;");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8);
        grid.setPadding(new Insets(20, 10, 10, 10));

        Map<String, Control> inputs = new LinkedHashMap<>();
        int row = 0;
        for (String field : fields) {
            // Skip fields that don't exist on this entity (e.g., phoneNumber not on User)
            if (!hasField(entity, field)) continue;
            String current = getField(entity, field);
            Control ctrl;
            if (isDropdownField(field)) {
                // Dropdown for neighbourhood: show names, store IDs
                ComboBox<String> cb = new ComboBox<>();
                cb.setPrefWidth(300);
                List<String> names = new ArrayList<>(neighbourhoodNames.values());
                java.util.Collections.sort(names);
                cb.getItems().addAll(names);
                if (current != null && !current.isBlank()) {
                    String name = neighbourhoodNames.getOrDefault(current, current);
                    cb.setValue(name);
                }
                ctrl = cb;
            } else {
                TextField tf = new TextField(current != null ? current : "");
                tf.setPrefWidth(300);
                ctrl = tf;
            }
            grid.add(new Label(field), 0, row);
            grid.add(ctrl, 1, row);
            inputs.put(field, ctrl);
            row++;
        }
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            Map<String, String> values = new LinkedHashMap<>();
            for (var e : inputs.entrySet()) {
                String newVal = getControlValue(e.getValue());
                String oldVal = getField(entity, e.getKey());
                if (isDropdownField(e.getKey())) {
                    // Convert display name → ID
                    for (var n : neighbourhoodNames.entrySet()) {
                        if (n.getValue().equals(newVal)) { newVal = n.getKey(); break; }
                    }
                }
                if (!newVal.equals(oldVal))
                    values.put(e.getKey(), newVal);
            }
            return values.isEmpty() ? null : values;
        });

        var result = dialog.showAndWait();
        result.ifPresent(changes -> saveEdit(tableName, entityType, entity, rowId, changes));
    }

    // ── Field access via reflection (records have accessor methods) ─────────

    private static <T> boolean hasField(T entity, String fieldName) {
        try { entity.getClass().getMethod(fieldName); return true; }
        catch (NoSuchMethodException e) { return false; }
    }

    private static <T> String getField(T entity, String fieldName) {
        try {
            var method = entity.getClass().getMethod(fieldName);
            Object val = method.invoke(entity);
            if (val == null) return "";
            if (val instanceof Enum<?> e) return e.name();
            if (val instanceof Instant i) return i.toString();
            return val.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ── Save edit ───────────────────────────────────────────────────────────

    private <T> void saveEdit(String tableName, String entityType,
                               T entity, String rowId, Map<String, String> changes) {
        try {
            // Build previous/new values
            Map<String, String> prev = new HashMap<>();
            Map<String, String> next = new HashMap<>();
            for (var e : changes.entrySet()) {
                if (!hasField(entity, e.getKey())) continue;
                prev.put(e.getKey(), getField(entity, e.getKey()));
                next.put(e.getKey(), e.getValue());
            }

            // Determine base_updated_at from the entity's updatedAt
            String baseUpdatedAt = getField(entity, "updatedAt");
            if (baseUpdatedAt != null && baseUpdatedAt.isBlank()) baseUpdatedAt = null;

            // Track in outbox
            ctx.getDb().syncChangelog().track(new ChangeEvent(
                    tableName, rowId, "UPDATE", prev, next, baseUpdatedAt, Instant.now()));

            // Apply changes to the entity and save
            T updated = applyChanges(entity, changes);
            saveEntity(tableName, updated);

            refreshAllTabs();
            ctx.getReporter().reportInfo(entityType + " " + rowId + ": " + changes.size() + " field(s) updated");
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            ctx.getReporter().reportWarning(t("viewer.edit.error") + ": " + cause.toString());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T applyChanges(T entity, Map<String, String> changes) {
        // Use reflection to create updated record — works for all entity types
        // because records have canonical constructors matching their accessor fields.
        try {
            var recordClass = entity.getClass();
            var components = recordClass.getRecordComponents();
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                String name = components[i].getName();
                if (changes.containsKey(name)) {
                    args[i] = parseValue(changes.get(name), components[i].getType());
                } else {
                    args[i] = components[i].getAccessor().invoke(entity);
                }
            }
            return (T) recordClass.getDeclaredConstructors()[0].newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply changes: " + e.getMessage(), e);
        }
    }

    private static Object parseValue(String val, Class<?> type) {
        if (type == String.class) return val;
        if (type == Integer.class || type == int.class) return Integer.parseInt(val);
        if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(val);
        if (type == Instant.class) return Instant.parse(val);
        if (type.isEnum()) {
            for (Object c : type.getEnumConstants()) {
                if (c.toString().equals(val)) return c;
            }
        }
        return val;
    }

    @SuppressWarnings("unchecked")
    private <T> void saveEntity(String tableName, T entity) {
        switch (tableName) {
            case "incidents" -> ctx.getDb().incidents().save((Incident) entity);
            case "users"     -> ctx.getDb().users().save((User) entity);
            case "listings"  -> ctx.getDb().listings().save((tech.nabor.api.model.listings.Listing) entity);
            case "events"    -> ctx.getDb().evenements().save((tech.nabor.api.model.events.Evenement) entity);
            default ->
                System.out.println("[Viewer] saveEntity: unsupported table " + tableName);
        }
    }

    // ── Neighbourhood tab ───────────────────────────────────────────────────

    private Tab buildNeighbourhoodTab() {
        List<MappingNeighbourhood> data = ctx.getDb().mappingNeighbourhoods().findAll();
        TableView<MappingNeighbourhood> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
                table.getColumns().addAll(
                col("Neighbourhood ID", MappingNeighbourhood::neighbourhoodId, 200, null, null, null),
                col("Name", MappingNeighbourhood::neighbourhoodName, 300, null, null, null));
        table.getItems().setAll(data);
        Map<String, Function<MappingNeighbourhood, String>> cols = new LinkedHashMap<>();
        cols.put("Neighbourhood ID", MappingNeighbourhood::neighbourhoodId);
        cols.put("Name", MappingNeighbourhood::neighbourhoodName);
        return wrapTab(t("viewer.tab.neighbourhoods"), table, data,
                n -> n.neighbourhoodId() + n.neighbourhoodName(), cols);
    }

    // ── History tab ─────────────────────────────────────────────────────────

    private Tab buildHistoryTab() {
        List<SyncChange> changes = ctx.getDb().syncChangelog().findAll();
        TableView<SyncChange> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Op column — color-coded
        TableColumn<SyncChange, String> opCol = new TableColumn<>("Op");
        opCol.setPrefWidth(60);
        opCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().operation()));
        opCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String op, boolean empty) {
                super.updateItem(op, empty);
                if (empty || op == null) { setText(null); setStyle(""); return; }
                setText(op);
                setStyle(switch (op) {
                    case "INSERT" -> "-fx-text-fill: #2E7D32; -fx-font-weight: bold;"; // green
                    case "DELETE" -> "-fx-text-fill: #C62828; -fx-font-weight: bold;"; // red
                    default       -> "-fx-text-fill: #E65100; -fx-font-weight: bold;"; // amber
                });
            }
        });

        // Changes column — context-dependent
        TableColumn<SyncChange, String> changesCol = new TableColumn<>("Changes");
        changesCol.setPrefWidth(300);
        changesCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(""));
        changesCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty) { setText(null); return; }
                SyncChange c = getTableView().getItems().get(getIndex());
                setText(switch (c.operation()) {
                    case "INSERT" -> "➕ " + fmtMap(c.newValues());
                    case "DELETE" -> "➖ " + fmtMap(c.previousValues());
                    default       -> fmtDiff(c.previousValues(), c.newValues());
                });
            }
        });

        TableColumn<SyncChange, String> tableCol = col("Table", SyncChange::tableName, 70, null, null, null);
        TableColumn<SyncChange, String> rowCol   = col("Row", SyncChange::rowId, 100, null, null, null);
        TableColumn<SyncChange, String> timeCol  = col("When", c -> fmt(c.changedAt()), 100, null, null, null);

        table.getColumns().addAll(opCol, tableCol, rowCol, changesCol, timeCol);

        // Undo button
        TableColumn<SyncChange, Void> actionsCol = new TableColumn<>("");
        actionsCol.setPrefWidth(50);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button undoBtn = new Button(t("viewer.history.undo"));
            { undoBtn.setOnAction(e -> rollbackChange(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : undoBtn);
            }
        });
        table.getColumns().add(actionsCol);
        table.getItems().setAll(changes);
        Map<String, Function<SyncChange, String>> cols = new LinkedHashMap<>();
        cols.put("Op", SyncChange::operation);
        cols.put("Table", SyncChange::tableName);
        cols.put("Row", SyncChange::rowId);
        cols.put("When", c -> fmt(c.changedAt()));
        return wrapTab(t("viewer.tab.history"), table, changes,
                c -> c.tableName() + c.rowId() + c.operation(), cols);
    }

    // ── Rollback ────────────────────────────────────────────────────────────

    private void rollbackChange(SyncChange change) {
        try {
            if ("incidents".equals(change.tableName())) {
                var opt = ctx.getDb().incidents().findById(change.rowId());
                if (opt.isPresent() && change.previousValues() != null) {
                    Incident cur = opt.get();
                    Map<String, String> pv = change.previousValues();
                    ctx.getDb().incidents().save(new Incident(
                            cur.id(), cur.reporterId(), cur.assignedTo(),
                            cur.neighbourhoodId(), cur.mongoDocumentId(),
                            pv.getOrDefault("title", cur.title()),
                            pv.getOrDefault("description", cur.description()),
                            parseSeverity(pv.getOrDefault("severity", cur.severity().name())),
                            parseStatus(pv.getOrDefault("status", cur.status().name())),
                            cur.assignedAt(), cur.createdAt(), Instant.now(), cur.resolvedAt()));
                }
            }
            ctx.getDb().syncChangelog().deleteById(change.id());
            refreshAllTabs();
        } catch (Exception e) {
            ctx.getReporter().reportWarning("Rollback failed: " + e.getMessage());
        }
    }

    private static tech.nabor.api.model.enums.IncidentSeverity parseSeverity(String s) {
        try { return tech.nabor.api.model.enums.IncidentSeverity.valueOf(s); }
        catch (Exception e) { return tech.nabor.api.model.enums.IncidentSeverity.medium; }
    }
    private static tech.nabor.api.model.enums.IncidentStatus parseStatus(String s) {
        try { return tech.nabor.api.model.enums.IncidentStatus.valueOf(s); }
        catch (Exception e) { return tech.nabor.api.model.enums.IncidentStatus.open; }
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    /** Returns true if the field should use a dropdown (neighbourhood picker) */
    private boolean isDropdownField(String fieldName) {
        return "neighbourhoodId".equals(fieldName);
    }

    private <T> TableColumn<T, String> col(String name, Function<T, String> extractor, double w,
                                            String fieldName,
                                            Map<String, Set<String>> dirtyMap, Function<T, String> idFn) {
        TableColumn<T, String> c = new TableColumn<>(name);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(extractor.apply(d.getValue())));
        // Make cells selectable via an invisible, read-only TextField
        c.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.TextField tf = new javafx.scene.control.TextField();
            {
                tf.setEditable(false);
                tf.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; "
                        + "-fx-background-radius: 0; -fx-padding: 0; -fx-border-width: 0;");
            }
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setGraphic(null); return; }
                tf.setText(val);
                tf.getStyleClass().remove("dirty-cell");
                T item = getTableView().getItems().get(getIndex());
                if (item != null && fieldName != null && dirtyMap != null && idFn != null) {
                    Set<String> fields = dirtyMap.get(idFn.apply(item));
                    if (fields != null && fields.contains(fieldName))
                        tf.getStyleClass().add("dirty-cell");
                }
                setGraphic(tf);
            }
        });
        return c;
    }

    private <T> Tab wrapTab(String title, TableView<T> table, List<T> data,
                            Function<T, String> searchFn,
                            Map<String, Function<T, String>> columnExtractors) {
        // Column filter dropdown
        ComboBox<String> columnBox = new ComboBox<>();
        columnBox.setPromptText(t("viewer.filter.column"));
        columnBox.setMinWidth(150);
        columnBox.setMaxWidth(200);
        List<String> colNames = new ArrayList<>();
        colNames.add(t("viewer.filter.all"));
        colNames.addAll(columnExtractors.keySet());
        columnBox.setItems(FXCollections.observableArrayList(colNames));
        columnBox.getSelectionModel().selectFirst();

        // Text filter
        TextField filter = new TextField();
        filter.setPromptText(t("viewer.filter"));
        filter.setMinWidth(180);
        HBox.setHgrow(filter, Priority.ALWAYS);

        FilteredList<T> filtered = new FilteredList<>(FXCollections.observableArrayList(data), p -> true);

        Runnable updateFilter = () -> {
            String text = filter.getText();
            String selectedCol = columnBox.getValue();
            boolean allColumns = selectedCol == null
                    || selectedCol.equals(t("viewer.filter.all"));
            Function<T, String> colFn = allColumns ? null : columnExtractors.get(selectedCol);

            filtered.setPredicate(item -> {
                if (text == null || text.isBlank()) return true;
                String lower = text.toLowerCase();
                if (colFn != null) {
                    String val = colFn.apply(item);
                    return val != null && val.toLowerCase().contains(lower);
                }
                String search = searchFn.apply(item);
                return search != null && search.toLowerCase().contains(lower);
            });
        };

        filter.textProperty().addListener((obs, old, text) -> updateFilter.run());
        columnBox.valueProperty().addListener((obs, old, val) -> updateFilter.run());

        table.setItems(filtered);

        HBox filterBar = new HBox(8, columnBox, filter);

        VBox box = new VBox(6, filterBar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setPadding(new Insets(8, 0, 0, 0));
        Tab tab = new Tab(title, box);
        tab.setClosable(false);
        return tab;
    }

    private static String getControlValue(Control c) {
        if (c instanceof TextField tf) return tf.getText();
        if (c instanceof ComboBox<?> cb) {
            Object val = cb.getValue();
            return val != null ? val.toString() : "";
        }
        return "";
    }

    private String resolveNb(String id) {
        if (id == null || id.isBlank()) return "—";
        return neighbourhoodNames.getOrDefault(id, id);
    }

    private static String fmtDiff(Map<String, String> prev, Map<String, String> next) {
        if (prev == null && next == null) return "—";
        if (prev == null) return fmtMap(next);
        if (next == null) return fmtMap(prev);
        StringBuilder sb = new StringBuilder();
        for (var e : next.entrySet()) {
            if (!sb.isEmpty()) sb.append(", ");
            String old = prev.getOrDefault(e.getKey(), "—");
            sb.append(e.getKey()).append(": ").append(old).append(" → ").append(e.getValue());
        }
        return sb.toString();
    }

    private static String fmtMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        for (var e : map.entrySet())
            sb.append(e.getValue());
        return sb.toString().trim();
    }

    private String fmt(Instant instant) {
        if (instant == null) return "—";
        return java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                java.time.format.FormatStyle.SHORT)
                .withLocale(java.util.Locale.forLanguageTag(bundle.getLocale().getLanguage()))
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant);
    }
}
