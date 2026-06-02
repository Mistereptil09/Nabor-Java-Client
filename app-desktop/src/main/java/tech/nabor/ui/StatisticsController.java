package tech.nabor.ui;

import java.util.Map;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import tech.nabor.AppContext;
import tech.nabor.api.EventBus;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.service.StatsService;
import tech.nabor.ui.i18n.I18nManager;


public class StatisticsController {

    @FXML private Label screenTitle;
    @FXML private Label kpiIncidentsValue;
    @FXML private Label kpiIncidentsLabel;
    @FXML private Label kpiEventsValue;
    @FXML private Label kpiEventsLabel;
    @FXML private Label kpiRegistrationsValue;
    @FXML private Label kpiRegistrationsLabel;
    @FXML private FlowPane chartsBox;

    private StatsService stats;
    private I18nManager i18n;

    public void init(AppContext app, I18nManager i18n) {
        this.i18n = i18n;
        this.stats = new StatsService(app.pluginContext());

        EventBus eventBus = app.pluginContext().getEventBus();
        eventBus.subscribe(UiEvents.INCIDENTS_CHANGED, payload -> Platform.runLater(this::refresh));

        i18n.onLocaleChange(this::refresh);
        refresh();
    }

    private void refresh() {
        applyTexts();
        loadKpis();
        loadCharts();
    }

    private void applyTexts() {
        screenTitle.setText(i18n.t("screen.stats.title"));
        kpiIncidentsLabel.setText(i18n.t("stats.kpi.incidents"));
        kpiEventsLabel.setText(i18n.t("stats.kpi.events"));
        kpiRegistrationsLabel.setText(i18n.t("stats.kpi.registrations"));
    }

    private void loadKpis() {
        kpiIncidentsValue.setText(String.valueOf(stats.totalIncidents()));
        kpiEventsValue.setText(String.valueOf(stats.totalEvents()));
        kpiRegistrationsValue.setText(String.valueOf(stats.totalRegistrations()));
    }

    private void loadCharts() {
        chartsBox.getChildren().setAll(buildStatusChart(), buildSeverityChart());
    }

    private BarChart<String, Number> buildStatusChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(i18n.t("stats.chart.byStatus"));
        chart.setLegendVisible(false);
        chart.setPrefSize(420, 300);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<IncidentStatus, Integer> entry : stats.incidentsByStatus().entrySet()) {
            String label = i18n.t("incidents.status." + entry.getKey().name());
            series.getData().add(new XYChart.Data<>(label, entry.getValue()));
        }
        chart.getData().add(series);
        return chart;
    }

    private PieChart buildSeverityChart() {
        PieChart chart = new PieChart();
        chart.setTitle(i18n.t("stats.chart.bySeverity"));
        chart.setPrefSize(420, 300);

        for (Map.Entry<IncidentSeverity, Integer> entry : stats.incidentsBySeverity().entrySet()) {
            if (entry.getValue() > 0) {
                String label = i18n.t("incidents.severity." + entry.getKey().name());
                chart.getData().add(new PieChart.Data(label + " (" + entry.getValue() + ")", entry.getValue()));
            }
        }
        return chart;
    }
}
