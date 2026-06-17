package tech.nabor.ui.dashboard;

import javafx.scene.Node;
import tech.nabor.api.PluginContext;

/** A self-contained dashboard widget. */
public abstract class DashboardTile {

    public abstract String getId();        // unique, e.g. "kpi-incidents"
    public abstract String getTitle();     // display name
    public abstract int getColSpan();      // 1 or 2
    public abstract Node build(PluginContext ctx);
    public abstract void refresh();

    /** Returns the header bar (title + close button). Override to customize. */
    public Node getHeader() {
        return null; // built by DashboardController
    }
}
