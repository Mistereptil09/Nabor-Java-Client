package tech.nabor.plugin.sync;

import javafx.scene.Node;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tech.nabor.api.model.sync.SyncChange;
import tech.nabor.api.model.sync.SyncState;
import com.fasterxml.jackson.databind.JsonNode;
import tech.nabor.api.model.sync.PendingConflict;
import java.util.List;

public class SyncPlugin implements NaborPlugin {

    private PluginContext ctx;

    @Override public String getId() { return "sync"; }
    @Override public String getDisplayName() { return "Sync"; }
    @Override public Optional<Node> getView() { return Optional.empty(); }
    @Override public void shutdown() {}

    @Override
    public void initialize(PluginContext ctx)
    {
        this.ctx = ctx;
        ctx.getEventBus().subscribe("sync.start", payload ->
                new Thread(this::doSync).start()
        );
    }

    private void doSync() {
        try
        {
            push();
            pull();
            ctx.getEventBus().publish("sync.completed", null);
        }
        catch (Exception e)
        {
            ctx.getEventBus().publish("sync.failed", e.getMessage());
        }
    }

    private void push() throws Exception{
        List<SyncChange> changes = ctx.getDb().syncChangelog().findUnsynced();
        if (changes.isEmpty()) return;

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("jobId", UUID.randomUUID().toString());

        ArrayNode updates = mapper.createArrayNode();
        for (SyncChange c : changes) {
            ObjectNode item = mapper.createObjectNode();
            item.put("entity_type", tableToEntityType(c.tableName()));
            item.put("entity_id", c.rowId());
            item.put("updated_at", c.changedAt().toString());

            ObjectNode changesNode = mapper.createObjectNode();
            if (c.newValues() != null)
                c.newValues().forEach(changesNode::put);
            item.set("changes", changesNode);
            updates.add(item);
        }
        body.set("updates", updates);

        ctx.getHttpClient().post("/sync/updates", mapper.writeValueAsString(body));
    }

    private void pull() throws Exception{
        String lastSync = ctx.getDb().syncState().get()
                .map(s->s.lastSyncedAt().toString())
                .orElse("1970-01-01T00:00:00Z");
        String url = "/sync/snapshot?since=" + lastSync;

        ObjectMapper mapper = new ObjectMapper();
        String cursor = null;
        boolean hasMore;

        do {
            String fullUrl = url + (cursor != null ? "&cursor=" + cursor : "");
            String response = ctx.getHttpClient().get(fullUrl);
            JsonNode root = mapper.readTree(response);

            processIncidents(root.get("incidents"));
            cursor = root.path("cursor").asText(null);
            hasMore = root.path("has_more").asBoolean(false);

            String syncAt = root.path("sync_at").asText();
            ctx.getDb().syncState().save(new SyncState(Instant.parse(syncAt), null, false));
        } while (hasMore);

    }

    private void processIncidents(JsonNode incidentsNode) throws Exception {
        if (incidentsNode == null || !incidentsNode.isArray()) return;
        for (JsonNode remote : incidentsNode) {
            String id = remote.path("id").asText();
            ctx.getDb().incidents().findById(id).ifPresent(local -> {
                checkConflict("incidents", id, "title",
                        local.title(), remote.path("title").asText(null));
                checkConflict("incidents", id, "description",
                        local.description(), remote.path("description").asText(null));
                checkConflict("incidents", id, "severity",
                        local.severity() != null ? local.severity().name() : null,
                        remote.path("severity").asText(null));
                checkConflict("incidents", id, "status",
                        local.status() != null ? local.status().name() : null,
                        remote.path("status").asText(null));
            });
        }
    }

    private void checkConflict(String table, String id, String field, String local, String remote) {
        String l = local == null ? "" : local;
        String r = remote == null ? "" : remote;
        if (l.equals(r)) return;
        ctx.getDb().pendingConflicts().save(new PendingConflict(0, table, id, field, local, remote, Instant.now()));
    }

    private String tableToEntityType(String table) {
        return switch (table) {
            case "incidents" -> "incident";
            case "listings" -> "listing";
            case "events" -> "event";
            case "users" -> "user";
            case "neighbourhoods" -> "neighbourhood";
            default -> table;
        };
    }
}
