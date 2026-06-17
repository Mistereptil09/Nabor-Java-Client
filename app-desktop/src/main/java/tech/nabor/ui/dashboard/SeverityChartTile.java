package tech.nabor.ui.dashboard;

import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.enums.IncidentSeverity;

/** Pie chart: incidents by severity. */
public class SeverityChartTile extends DashboardTile {

    private PieChart chart;

    @Override public String getId() { return "chart-severity"; }
    @Override public String getTitle() { return "Incidents by Severity"; }
    @Override public int getColSpan() { return 2; }

    @Override
    public Node build(PluginContext ctx) {
        chart = new PieChart();
        chart.setPrefHeight(250);
        for (IncidentSeverity s : IncidentSeverity.values()) {
            int count = ctx.getDb().incidents().findBySeverity(s, 1000).size();
            if (count > 0)
                chart.getData().add(new PieChart.Data(s.name() + " (" + count + ")", count));
        }
        return chart;
    }

    @Override
    public void refresh() {}
}
