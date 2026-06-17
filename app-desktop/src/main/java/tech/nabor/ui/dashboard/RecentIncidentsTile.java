package tech.nabor.ui.dashboard;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.incidents.Incident;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** Compact table showing the 10 most recent incidents. */
public class RecentIncidentsTile extends DashboardTile {

    private TableView<Incident> table;

    @Override public String getId() { return "table-recent"; }
    @Override public String getTitle() { return "Recent Incidents"; }
    @Override public int getColSpan() { return 2; }

    @Override
    public Node build(PluginContext ctx) {
        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(220);

        TableColumn<Incident, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().title()));
        titleCol.setPrefWidth(200);

        TableColumn<Incident, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().status().name()));
        statusCol.setPrefWidth(80);

        TableColumn<Incident, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().severity().name()));
        sevCol.setPrefWidth(80);

        TableColumn<Incident, String> dateCol = new TableColumn<>("Created");
        dateCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(fmt(d.getValue().createdAt())));
        dateCol.setPrefWidth(130);

        table.getColumns().addAll(titleCol, statusCol, sevCol, dateCol);

        var items = ctx.getDb().incidents().findAll().stream()
                .sorted((a, b) -> {
                    Instant ca = a.createdAt(), cb = b.createdAt();
                    if (ca == null) return 1;
                    if (cb == null) return -1;
                    return cb.compareTo(ca);
                }).limit(10).toList();
        table.setItems(FXCollections.observableArrayList(items));
        return table;
    }

    private static String fmt(Instant i) {
        if (i == null) return "—";
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault()).format(i);
    }

    @Override public void refresh() {}
}
