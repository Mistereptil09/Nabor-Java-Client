package tech.nabor.ui;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.Chart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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
    @FXML private Label kpiResolutionValue;
    @FXML private Label kpiResolutionLabel;
    @FXML private Label kpiPickupValue;
    @FXML private Label kpiPickupLabel;
    @FXML private Label kpiWeekHoursValue;
    @FXML private Label kpiWeekHoursLabel;
    @FXML private VBox chartsBox;

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
        kpiResolutionLabel.setText(i18n.t("stats.kpi.resolution"));
        kpiPickupLabel.setText(i18n.t("stats.kpi.pickup"));
        kpiWeekHoursLabel.setText(i18n.t("stats.kpi.weekHours"));
    }

    private void loadKpis() {
        kpiIncidentsValue.setText(String.valueOf(stats.totalIncidents()));
        kpiEventsValue.setText(String.valueOf(stats.totalEvents()));
        kpiRegistrationsValue.setText(String.valueOf(stats.totalRegistrations()));
        kpiResolutionValue.setText(formatDuration(stats.averageResolutionTime()));
        kpiPickupValue.setText(formatDuration(stats.averagePickupTime()));
        kpiWeekHoursValue.setText(String.format("%.1f h", stats.incidentHoursThisWeek()));
    }

    private String formatDuration(Optional<Duration> duration) {
        if (duration.isEmpty()) {
            return "—";
        }
        long minutes = duration.get().toMinutes();
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " h " + (minutes % 60) + " min";
        }
        long days = hours / 24;
        return days + " j " + (hours % 24) + " h";
    }

    private void loadCharts() {
        Chart trend = grow(buildTrendChart());
        HBox row1 = new HBox(18, grow(buildResolutionBySeverityChart()), grow(buildStatusChart()));
        HBox row2 = new HBox(18, grow(buildSeverityChart()));
        chartsBox.getChildren().setAll(trend, row1, row2);
    }

    private <T extends Chart> T grow(T chart) {
        chart.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chart, Priority.ALWAYS);
        return chart;
    }

    private LineChart<String, Number> buildTrendChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(i18n.t("stats.chart.trend"));
        chart.setPrefHeight(320);
        chart.setCreateSymbols(true);

        XYChart.Series<String, Number> created = new XYChart.Series<>();
        created.setName(i18n.t("stats.chart.trend.created"));
        XYChart.Series<String, Number> resolved = new XYChart.Series<>();
        resolved.setName(i18n.t("stats.chart.trend.resolved"));

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM");
        for (StatsService.DailyCount dc : stats.creationVsResolutionTrend(14)) {
            String label = dc.day().format(dayFmt);
            created.getData().add(new XYChart.Data<>(label, dc.created()));
            resolved.getData().add(new XYChart.Data<>(label, dc.resolved()));
        }
        chart.getData().add(created);
        chart.getData().add(resolved);
        return chart;
    }

    private BarChart<String, Number> buildResolutionBySeverityChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(i18n.t("stats.chart.resolutionBySeverity"));
        chart.setLegendVisible(false);
        chart.setPrefHeight(300);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        stats.resolutionTimeBySeverity().forEach((severity, duration) -> {
            String label = i18n.t("incidents.severity." + severity.name());
            double hours = duration.map(d -> d.toMinutes() / 60.0).orElse(0.0);
            series.getData().add(new XYChart.Data<>(label, hours));
        });
        chart.getData().add(series);
        return chart;
    }

    private BarChart<String, Number> buildStatusChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(i18n.t("stats.chart.byStatus"));
        chart.setLegendVisible(false);
        chart.setPrefHeight(300);

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
        chart.setPrefHeight(300);

        for (Map.Entry<IncidentSeverity, Integer> entry : stats.incidentsBySeverity().entrySet()) {
            if (entry.getValue() > 0) {
                String label = i18n.t("incidents.severity." + entry.getKey().name());
                chart.getData().add(new PieChart.Data(label + " (" + entry.getValue() + ")", entry.getValue()));
            }
        }
        return chart;
    }
}
