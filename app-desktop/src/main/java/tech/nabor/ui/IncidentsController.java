package tech.nabor.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import tech.nabor.AppContext;
import tech.nabor.api.EventBus;
import tech.nabor.api.SqliteRepository;
import tech.nabor.api.error.NaborException;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.MappingNeighbourhood;
import tech.nabor.api.model.user.User;
import tech.nabor.service.IncidentService;
import tech.nabor.ui.i18n.I18nManager;


public class IncidentsController {

    @FXML private Label screenTitle;
    @FXML private Label filterLabel;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button newButton;
    @FXML private TableView<Incident> table;
    @FXML private TableColumn<Incident, String> titleCol;
    @FXML private TableColumn<Incident, String> severityCol;
    @FXML private TableColumn<Incident, String> statusCol;
    @FXML private TableColumn<Incident, String> reporterCol;
    @FXML private TableColumn<Incident, String> assignedCol;
    @FXML private TableColumn<Incident, String> neighbourhoodCol;
    @FXML private TableColumn<Incident, String> createdCol;
    @FXML private Label detailTitle;
    @FXML private Label detailMeta;
    @FXML private Label detailDescription;
    @FXML private Button assignButton;
    @FXML private Button resolveButton;
    @FXML private Button deleteButton;

    private IncidentService service;
    private I18nManager i18n;
    private NaborReporter reporter;
    private SqliteRepository db;
    private Map<String,String> nbNames = Map.of();
    private Map<String,String> userNames = Map.of();

    public void init(AppContext app, I18nManager i18n) {
        this.i18n = i18n;
        this.service = new IncidentService(app.pluginContext());
        this.reporter = app.pluginContext().getReporter();
        this.db = app.pluginContext().getDb();

        setupFilter();
        setupColumns();
        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> showDetail(selected));

        EventBus eventBus = app.pluginContext().getEventBus();
        eventBus.subscribe(UiEvents.INCIDENTS_CHANGED,
                payload -> Platform.runLater(this::reload));

        i18n.onLocaleChange(this::applyTexts);
        applyTexts();
        reload();
    }

    private void loadLookups() {
        nbNames = db.mappingNeighbourhoods().findAll().stream()
                .collect(Collectors.toMap(MappingNeighbourhood::neighbourhoodId,
                        MappingNeighbourhood::neighbourhoodName, (a,b) -> a));
        userNames = new HashMap<>();
        for (User u : db.users().findAll())
            userNames.put(u.id(), u.firstName() + " " + u.lastName());
    }

    private String resolveUser(String userId) {
        if (userId == null) return "—";
        return userNames.getOrDefault(userId, userId);
    }

    private String resolveNb(String nid) {
        if (nid == null || nid.isBlank()) return "—";
        return nbNames.getOrDefault(nid, nid);
    }

    private void setupFilter() {
        statusFilter.setItems(FXCollections.observableArrayList("all", "open", "in_progress", "resolved"));
        statusFilter.setConverter(new StringConverter<>() {
            @Override public String toString(String key) {
                return key == null ? "" : i18n.t("incidents.filter." + key);
            }
            @Override public String fromString(String s) { return s; }
        });
        statusFilter.getSelectionModel().select("all");
        statusFilter.valueProperty().addListener((o, a, b) -> reload());
    }

    private void setupColumns() {
        titleCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().title()));
        severityCol.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(i18n.t("incidents.severity." + c.getValue().severity().name())));
        statusCol.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(i18n.t("incidents.status." + c.getValue().status().name())));
        reporterCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(resolveUser(c.getValue().reporterId())));
        assignedCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(resolveUser(c.getValue().assignedTo())));
        neighbourhoodCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(resolveNb(c.getValue().neighbourhoodId())));
        createdCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatDate(c.getValue().createdAt())));
    }

    private void applyTexts() {
        screenTitle.setText(i18n.t("screen.incidents.title"));
        filterLabel.setText(i18n.t("incidents.filter"));
        newButton.setText(i18n.t("incidents.new"));
        titleCol.setText(i18n.t("incidents.col.title"));
        severityCol.setText(i18n.t("incidents.col.severity"));
        statusCol.setText(i18n.t("incidents.col.status"));
        reporterCol.setText("Reporter");
        assignedCol.setText("Assigned to");
        neighbourhoodCol.setText("Neighbourhood");
        createdCol.setText(i18n.t("incidents.col.created"));
        assignButton.setText(i18n.t("incidents.assign"));
        resolveButton.setText(i18n.t("incidents.resolve"));
        deleteButton.setText("Delete");

        String selected = statusFilter.getValue();
        statusFilter.setItems(FXCollections.observableArrayList("all", "open", "in_progress", "resolved"));
        statusFilter.getSelectionModel().select(selected != null ? selected : "all");
        table.refresh();
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    private void reload() {
        loadLookups();
        String key = statusFilter.getValue();
        IncidentStatus filter = (key == null || key.equals("all")) ? null : IncidentStatus.valueOf(key);

        Incident previous = table.getSelectionModel().getSelectedItem();
        String previousId = previous != null ? previous.id() : null;

        List<Incident> items = service.list(filter);
        table.setItems(FXCollections.observableArrayList(items));

        if (previousId != null) {
            table.getItems().stream()
                    .filter(i -> i.id().equals(previousId))
                    .findFirst()
                    .ifPresent(i -> table.getSelectionModel().select(i));
        }
    }

    private void showDetail(Incident incident) {
        if (incident == null) {
            detailTitle.setText(i18n.t("incidents.detail.empty"));
            detailMeta.setText("");
            detailDescription.setText("");
            assignButton.setDisable(true);
            resolveButton.setDisable(true);
            return;
        }
        detailTitle.setText(incident.title());
        detailMeta.setText(i18n.t("incidents.meta",
                i18n.t("incidents.severity." + incident.severity().name()),
                i18n.t("incidents.status." + incident.status().name()),
                formatDate(incident.createdAt())));
        detailDescription.setText(incident.description() == null ? "" : incident.description());

        boolean resolved = incident.status() == IncidentStatus.resolved;
        assignButton.setDisable(resolved);
        resolveButton.setDisable(resolved);
    }

    @FXML
    private void onAssign() {
        Incident selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            reporter.reportWarning(i18n.t("incidents.select.first"));
            return;
        }
        try {
            service.assignToMe(selected.id());
            reporter.reportInfo(i18n.t("incidents.assigned.toast"));
            reload();
        } catch (RuntimeException e) {
            reporter.reportError(new NaborException(
                    NaborException.Kind.DB_ERROR, "assign: " + e.getMessage(), e));
        }
    }

    @FXML
    private void onDelete() {
        Incident selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { reporter.reportWarning("Select an incident first"); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.title() + "\"? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Incident");
        confirm.setHeaderText("Confirm deletion");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                db.incidents().delete(selected.id());
                reporter.reportInfo("Incident deleted: " + selected.title());
                reload();
            }
        });
    }

    @FXML
    private void onResolve() {
        Incident selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            reporter.reportWarning(i18n.t("incidents.select.first"));
            return;
        }
        try {
            service.resolve(selected.id());
            reporter.reportInfo(i18n.t("incidents.resolved.toast"));
            reload();
        } catch (RuntimeException e) {
            reporter.reportError(new NaborException(
                    NaborException.Kind.DB_ERROR, "resolve: " + e.getMessage(), e));
        }
    }

    @FXML
    private void onNew() {
        Dialog<Incident> dialog = new Dialog<>();
        dialog.setTitle(i18n.t("incidents.dialog.title"));

        ButtonType okType = new ButtonType(i18n.t("incidents.dialog.ok"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        TextField titleField = new TextField();
        TextArea descriptionField = new TextArea();
        descriptionField.setPrefRowCount(3);

        ComboBox<IncidentSeverity> severityBox =
                new ComboBox<>(FXCollections.observableArrayList(IncidentSeverity.values()));
        severityBox.setConverter(new StringConverter<>() {
            @Override public String toString(IncidentSeverity s) {
                return s == null ? "" : i18n.t("incidents.severity." + s.name());
            }
            @Override public IncidentSeverity fromString(String s) {
                return null;
            }
        });
        severityBox.getSelectionModel().select(IncidentSeverity.medium);

        ComboBox<String> neighbourhoodBox = new ComboBox<>();
        neighbourhoodBox.setPrefWidth(300);
        List<String> nbNamesSorted = new ArrayList<>(nbNames.values());
        java.util.Collections.sort(nbNamesSorted);
        neighbourhoodBox.getItems().addAll(nbNamesSorted);
        neighbourhoodBox.setPromptText("(none)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label(i18n.t("incidents.field.title")), titleField);
        grid.addRow(1, new Label(i18n.t("incidents.field.description")), descriptionField);
        grid.addRow(2, new Label(i18n.t("incidents.field.severity")), severityBox);
        grid.addRow(3, new Label("Neighbourhood"), neighbourhoodBox);
        dialog.getDialogPane().setContent(grid);

        if (table.getScene() != null) {
            dialog.getDialogPane().getStylesheets().setAll(table.getScene().getStylesheets());
        }

        Node okButton = dialog.getDialogPane().lookupButton(okType);
        okButton.setDisable(true);
        titleField.textProperty().addListener((o, a, b) -> okButton.setDisable(b == null || b.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != okType) return null;
            String nbName = neighbourhoodBox.getValue();
            String nbId = null;
            if (nbName != null && !nbName.isBlank()) {
                for (var e : nbNames.entrySet()) {
                    if (e.getValue().equals(nbName)) { nbId = e.getKey(); break; }
                }
            }
            return service.create(titleField.getText().trim(), descriptionField.getText(),
                    severityBox.getValue(), nbId);
        });

        Optional<Incident> result = dialog.showAndWait();
        result.ifPresent(incident -> {
            reporter.reportInfo("Incident created: title=" + incident.title()
                    + ", severity=" + incident.severity().name()
                    + ", status=" + incident.status().name());
            reload();
        });
    }

    private String formatDate(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(i18n.locale()))
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
}
