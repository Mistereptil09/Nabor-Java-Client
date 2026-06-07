package tech.nabor.ui;

public final class UiEvents {

    private UiEvents() {}

    public static final String INCIDENTS_CHANGED = "ui.incidents.changed";
    public static final String SYNC_CHANGED      = "ui.sync.changed";
    /** Published when a plugin is loaded, unloaded, enabled, or disabled. */
    public static final String PLUGINS_CHANGED    = "ui.plugins.changed";
    /** Published when a network error occurs. Payload is the error message. */
    public static final String NETWORK_ERROR      = "ui.network.error";
}
