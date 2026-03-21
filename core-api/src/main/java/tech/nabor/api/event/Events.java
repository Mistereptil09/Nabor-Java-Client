package tech.nabor.api.event;

public final class Events {
    private Events() {}

    // Format : "db.<table>.<operation>"
    public static final String DB_INSERT = "db.insert";
    public static final String DB_UPDATE = "db.update";
    public static final String DB_DELETE = "db.delete";

    // Synchro
    public static final String SYNC_REQUESTED  = "sync.requested";   // user want to push
    public static final String SYNC_COMPLETED  = "sync.completed";   // successful push
    public static final String SYNC_CONFLICT   = "sync.conflict";    // detected conflicts
    public static final String SYNC_ROLLED_BACK = "sync.rolled_back"; // annulation
}