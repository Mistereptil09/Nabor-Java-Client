package tech.nabor.ui.dashboard;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import tech.nabor.api.PluginContext;
import tech.nabor.api.SqliteRepository;

import java.util.function.Function;

/** Single-stat KPI card, optionally with a subtitle. */
public class KpiTile extends DashboardTile {

    private final String id, title;
    private final Function<SqliteRepository, String> valueFn;
    private final Function<SqliteRepository, String> subtitleFn;
    private Label valueLabel, subtitleLabel;

    public KpiTile(String id, String title, Function<SqliteRepository, String> valueFn) {
        this(id, title, valueFn, null);
    }

    public KpiTile(String id, String title,
                   Function<SqliteRepository, String> valueFn,
                   Function<SqliteRepository, String> subtitleFn) {
        this.id = id;
        this.title = title;
        this.valueFn = valueFn;
        this.subtitleFn = subtitleFn;
    }

    @Override public String getId() { return id; }
    @Override public String getTitle() { return title; }
    @Override public int getColSpan() { return 1; }

    @Override
    public Node build(PluginContext ctx) {
        valueLabel = new Label();
        valueLabel.getStyleClass().add("kpi-value");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-label");

        VBox box = new VBox(4, valueLabel, titleLabel);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("kpi-card");

        if (subtitleFn != null) {
            subtitleLabel = new Label();
            subtitleLabel.getStyleClass().add("kpi-label");
            subtitleLabel.setStyle("-fx-font-size: 11px;");
            box.getChildren().add(1, subtitleLabel); // between value and title
        }

        refreshValue(ctx.getDb());
        return box;
    }

    @Override public void refresh() {}

    public void refreshValue(SqliteRepository db) {
        if (valueLabel != null) valueLabel.setText(valueFn.apply(db));
        if (subtitleLabel != null && subtitleFn != null)
            subtitleLabel.setText(subtitleFn.apply(db));
    }
}
