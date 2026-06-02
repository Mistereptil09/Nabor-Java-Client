package tech.nabor.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import tech.nabor.AppContext;
import tech.nabor.api.EventBus;
import tech.nabor.api.error.NaborException;
import tech.nabor.api.error.NaborReporter;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
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
    @FXML private TableColumn<Incident, String> createdCol;
    @FXML private Label detailTitle;
    @FXML private Label detailMeta;
    @FXML private Label detailDescription;
    @FXML private Button assignButton;
    @FXML private Button resolveButton;

    private IncidentService service;
    private I18nManager i18n;
    private NaborReporter reporter;

    public void init(AppContext app, I18nManager i18n) {
        this.i18n = i18n;
        this.service = new IncidentService(app.pluginContext());
        this.reporter = app.pluginContext().getReporter();

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

    private void setupFilter() {
        statusFilter.setItems(FXCollections.observableArrayList("all", "open", "in_progress", "resolved"));
        statusFilter.setConverter(new StringConverter<>() {
            @Override public String toString(String key) {
                return key == null ? "" : i18n.t("incidents.filter." + key);
            }
            @Override public String fromString(String s) {
                return s;
            }
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
        createdCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(formatDate(c.getValue().createdAt())));
    }

    private void applyTexts() {
        screenTitle.setText(i18n.t("screen.incidents.title"));
        filterLabel.setText(i18n.t("incidents.filter"));
        newButton.setText(i18n.t("incidents.new"));
        titleCol.setText(i18n.t("incidents.col.title"));
        severityCol.setText(i18n.t("incidents.col.severity"));
        statusCol.setText(i18n.t("incidents.col.status"));
        createdCol.setText(i18n.t("incidents.col.created"));
        assignButton.setText(i18n.t("incidents.assign"));
        resolveButton.setText(i18n.t("incidents.resolve"));

        String selected = statusFilter.getValue();
        statusFilter.setItems(FXCollections.observableArrayList("all", "open", "in_progress", "resolved"));
        statusFilter.getSelectionModel().select(selected != null ? selected : "all");
        table.refresh();
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    private void reload() {
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

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label(i18n.t("incidents.field.title")), titleField);
        grid.addRow(1, new Label(i18n.t("incidents.field.description")), descriptionField);
        grid.addRow(2, new Label(i18n.t("incidents.field.severity")), severityBox);
        dialog.getDialogPane().setContent(grid);

        if (table.getScene() != null) {
            dialog.getDialogPane().getStylesheets().setAll(table.getScene().getStylesheets());
        }

        Node okButton = dialog.getDialogPane().lookupButton(okType);
        okButton.setDisable(true);
        titleField.textProperty().addListener((o, a, b) -> okButton.setDisable(b == null || b.isBlank()));

        dialog.setResultConverter(button -> button == okType
                ? service.create(titleField.getText().trim(), descriptionField.getText(), severityBox.getValue())
                : null);

        Optional<Incident> result = dialog.showAndWait();
        result.ifPresent(incident -> {
            reporter.reportInfo(i18n.t("incidents.created.toast", incident.title()));
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
