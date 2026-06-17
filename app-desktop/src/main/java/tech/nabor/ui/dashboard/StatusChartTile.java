package tech.nabor.ui.dashboard;

import javafx.scene.Node;
import javafx.scene.chart.*;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.enums.IncidentStatus;

/** Bar chart: incidents count by status. */
public class StatusChartTile extends DashboardTile {

    private BarChart<String, Number> chart;

    @Override public String getId() { return "chart-status"; }
    @Override public String getTitle() { return "Incidents by Status"; }
    @Override public int getColSpan() { return 2; }

    @Override
    public Node build(PluginContext ctx) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setPrefHeight(250);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (IncidentStatus s : IncidentStatus.values()) {
            series.getData().add(new XYChart.Data<>(s.name(),
                    ctx.getDb().incidents().findByStatus(s, 1000).size()));
        }
        chart.getData().add(series);
        return chart;
    }

    @Override
    public void refresh() {
        if (chart == null) return;
        // Updated via DashboardController.reload()
    }
}
