package tech.nabor.ui.dashboard;

import javafx.scene.Node;
import javafx.scene.chart.*;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.enums.IncidentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Line chart: created vs resolved incidents over last 14 days. */
public class TrendChartTile extends DashboardTile {

    private LineChart<String, Number> chart;

    @Override public String getId() { return "chart-trend"; }
    @Override public String getTitle() { return "14-Day Trend"; }
    @Override public int getColSpan() { return 2; }

    @Override
    public Node build(PluginContext ctx) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        chart = new LineChart<>(x, y);
        chart.setPrefHeight(250);

        XYChart.Series<String, Number> created = new XYChart.Series<>();
        created.setName("Created");
        XYChart.Series<String, Number> resolved = new XYChart.Series<>();
        resolved.setName("Resolved");

        var all = ctx.getDb().incidents().findAll();
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String label = day.getMonthValue() + "/" + day.getDayOfMonth();
            long c = all.stream().filter(inc -> onDay(inc.createdAt(), day)).count();
            long r = all.stream().filter(inc -> inc.status() == IncidentStatus.resolved
                    && onDay(inc.resolvedAt(), day)).count();
            created.getData().add(new XYChart.Data<>(label, c));
            resolved.getData().add(new XYChart.Data<>(label, r));
        }
        chart.getData().addAll(created, resolved);
        return chart;
    }

    private static boolean onDay(Instant i, LocalDate day) {
        if (i == null) return false;
        return i.atZone(ZoneId.systemDefault()).toLocalDate().equals(day);
    }

    @Override public void refresh() {}
}
