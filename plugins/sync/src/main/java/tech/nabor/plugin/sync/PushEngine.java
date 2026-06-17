package tech.nabor.plugin.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tech.nabor.api.PluginContext;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.api.model.sync.SyncChange;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles push (client → server): reads outbox (sync_changelog),
 * POSTs to /sync/updates, processes per-entity results.
 * Applied → delete from outbox. Conflict → keep in outbox + record in pending_conflicts.
 */
class PushEngine {

    private final PluginContext ctx;
    private final ObjectMapper mapper = new ObjectMapper();

    PushEngine(PluginContext ctx) {
        this.ctx = ctx;
    }

    /** @return true if there were changes to push, false if outbox was empty */
    boolean push() throws Exception {
        List<SyncChange> batch = ctx.getDb().syncChangelog().findAll();
        if (batch.isEmpty()) return false;

        ObjectNode body = mapper.createObjectNode();
        body.put("jobId", UUID.randomUUID().toString());

        ArrayNode updates = mapper.createArrayNode();
        for (SyncChange c : batch) {
            ObjectNode item = mapper.createObjectNode();
            item.put("entity_type", toApiEntityType(c.tableName()));
            item.put("entity_id", c.rowId());
            item.put("action", operationToAction(c.operation()));

            if (c.newValues() != null) {
                ObjectNode changes = mapper.createObjectNode();
                c.newValues().forEach(changes::put);
                item.set("changes", changes);
            } else {
                item.set("changes", mapper.createObjectNode());
            }
            // base_updated_at required for update/delete; null = entity never modified on server
            if (!"INSERT".equals(c.operation())) {
                item.put("base_updated_at", c.baseUpdatedAt());
            }
            updates.add(item);
        }
        body.set("updates", updates);

        String respJson = ctx.getHttpClient().post("/sync/updates", mapper.writeValueAsString(body));
        JsonNode response = mapper.readTree(respJson);

        JsonNode results = response.path("results");
        if (results.isArray()) {
            for (JsonNode r : results) {
                String tableName = toDbTableName(r.path("entity_type").asText());
                String entityId = r.path("entity_id").asText();
                String status = r.path("status").asText();

                if ("applied".equals(status)) {
                    ctx.getDb().syncChangelog().deleteByTableAndRow(tableName, entityId);
                } else if ("skipped".equals(status)) {
                    String reason = r.path("reason").asText("unknown");
                    System.out.println("[Sync] Push SKIPPED " + tableName + "/" + entityId
                            + ": " + reason);
                } else if ("conflict".equals(status)) {
                    JsonNode conflict = r.path("conflict");
                    String fieldName = conflict.path("field_name").asText(null);
                    try {
                        JsonNode serverData = conflict.path("server_data");
                        // Apply server data to local entity immediately
                        applyServerData(tableName, entityId, serverData);
                        // Save conflict for UI resolution
                        ctx.getDb().pendingConflicts().save(new PendingConflict(
                                0, tableName, entityId, fieldName,
                                mapper.writeValueAsString(conflict.path("client_data")),
                                mapper.writeValueAsString(serverData),
                                Instant.now()));
                    } catch (Exception e) {
                        System.out.println("[Sync] Push conflict save failed: " + e.getMessage());
                    }
                }
            }
        }

        int applied = response.path("applied_count").asInt(0);
        boolean hasConflicts = response.path("has_conflicts").asBoolean(false);
        if (applied > 0) {
            ctx.getEventBus().publish("sync.push.completed", null);
        } else if (hasConflicts) {
            ctx.getEventBus().publish("sync.push.failed", null);
        }
        return applied > 0; // only pull if something was actually applied
    }

    // ── Apply server data on conflict ───────────────────────────────────

    /** Overwrites local entity with server_data from conflict response. */
    private void applyServerData(String tableName, String entityId, JsonNode data) {
        try {
            switch (tableName) {
                case "incidents" -> {
                    var opt = ctx.getDb().incidents().findById(entityId);
                    if (opt.isEmpty()) return;
                    var i = opt.get();
                    ctx.getDb().incidents().save(new tech.nabor.api.model.incidents.Incident(
                            i.id(), i.reporterId(), i.assignedTo(), i.neighbourhoodId(),
                            i.mongoDocumentId(),
                            data.path("title").asText(i.title()),
                            data.path("description").asText(i.description()),
                            parseSeverity(data.path("severity").asText(i.severity().name())),
                            parseStatus(data.path("status").asText(i.status().name())),
                            i.assignedAt(), i.createdAt(), Instant.now(), i.resolvedAt()));
                }
                case "users" -> {
                    var opt = ctx.getDb().users().findById(entityId);
                    if (opt.isEmpty()) return;
                    var u = opt.get();
                    ctx.getDb().users().save(new tech.nabor.api.model.user.User(
                            u.id(),
                            data.path("firstName").asText(u.firstName()),
                            data.path("lastName").asText(u.lastName()),
                            data.path("email").asText(u.email()),
                            u.passwordHash(), u.totpSecret(), u.stripeAccountId(),
                            data.path("neighbourhoodId").asText(u.neighbourhoodId()),
                            tech.nabor.api.model.enums.Visibility.valueOf(
                                    data.path("visibility").asText(u.visibility().name())),
                            data.path("bio").asText(u.bio()),
                            tech.nabor.api.model.enums.MessagePolicy.valueOf(
                                    data.path("messagePolicy").asText(u.messagePolicy().name())),
                            data.path("locale").asText(u.locale()),
                            u.profilePictureMongoId(), u.bannerMongoId(),
                            tech.nabor.api.model.enums.UserRole.valueOf(
                                    data.path("role").asText(u.role().name())),
                            u.lastLoginAt(), u.passwordChangedAt(),
                            u.createdAt(), Instant.now(), u.deletedAt()));
                }
            }
        } catch (Exception e) {
            System.out.println("[Sync] applyServerData failed: " + e.getMessage());
        }
    }

    private static tech.nabor.api.model.enums.IncidentSeverity parseSeverity(String s) {
        try { return tech.nabor.api.model.enums.IncidentSeverity.valueOf(s); }
        catch (Exception e) { return tech.nabor.api.model.enums.IncidentSeverity.medium; }
    }
    private static tech.nabor.api.model.enums.IncidentStatus parseStatus(String s) {
        try { return tech.nabor.api.model.enums.IncidentStatus.valueOf(s); }
        catch (Exception e) { return tech.nabor.api.model.enums.IncidentStatus.open; }
    }

    // ── Mappings ───────────────────────────────────────────────────────────

    static String operationToAction(String op) {
        return switch (op) {
            case "INSERT" -> "create";
            case "UPDATE" -> "update";
            case "DELETE" -> "delete";
            default       -> "update";
        };
    }

    // ── Entity type mapping ────────────────────────────────────────────────

    static String toApiEntityType(String tableName) {
        return switch (tableName) {
            case "incidents" -> "incident";
            case "users"     -> "user";
            case "listings"  -> "listing";
            case "evenements" -> "event";
            default          -> tableName;
        };
    }

    static String toDbTableName(String entityType) {
        return switch (entityType) {
            case "incident" -> "incidents";
            case "user"     -> "users";
            case "listing"  -> "listings";
            case "event"    -> "evenements";
            default         -> entityType;
        };
    }
}
