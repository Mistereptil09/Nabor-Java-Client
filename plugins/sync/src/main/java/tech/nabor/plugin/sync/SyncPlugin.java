package tech.nabor.plugin.sync;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import tech.nabor.api.NaborPlugin;
import tech.nabor.api.PluginContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.sync.SyncState;
import com.fasterxml.jackson.databind.JsonNode;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.api.model.sync.ResolvedConflict;

public class SyncPlugin implements NaborPlugin {

    private PluginContext ctx;
    private final ObjectMapper mapper = new ObjectMapper();
    private ResourceBundle bundle;

    // View fields — built lazily
    private VBox root;
    private Label titleLabel;
    private Label statusLabel;
    private Button syncButton;
    private Button syncAllButton;

    @Override public String getId() { return "sync"; }
    @Override public String getDisplayName() { return "Sync"; }
    @Override public void shutdown() {}

    @Override
    public Optional<Node> getView() {
        if (root == null) {
            try { buildUi(); } catch (Throwable e) { return Optional.empty(); }
        }
        return Optional.of(root);
    }

    @Override
    public void initialize(PluginContext ctx) {
        this.ctx = ctx;
        this.bundle = loadBundle();

        ctx.getEventBus().subscribe("sync.start", payload ->
                new Thread(this::doSync).start());

        ctx.getEventBus().subscribe("sync.full", payload ->
                new Thread(this::doFullSync).start());

        ctx.getEventBus().subscribe("sync.completed", payload -> refreshView());
        ctx.getEventBus().subscribe("sync.failed", payload -> refreshView());
    }

    // ── i18n ──────────────────────────────────────────────────────────────────

    private ResourceBundle loadBundle() {
        String locale = "fr";
        try { locale = ctx.getI18n().getLocale(); } catch (Exception ignored) {}
        return ResourceBundle.getBundle("i18n/sync/messages",
                java.util.Locale.forLanguageTag(locale));
    }

    private String t(String key, Object... args) {
        try {
            return java.text.MessageFormat.format(bundle.getString(key), args);
        } catch (Exception e) {
            return key;
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUi() {
        titleLabel = new Label(t("sync.title"));
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 13px;");

        syncButton = new Button(t("sync.now"));
        syncButton.getStyleClass().add("accent-button");
        syncButton.setOnAction(e -> ctx.getEventBus().publish("sync.start", null));

        syncAllButton = new Button(t("sync.all"));
        syncAllButton.getStyleClass().add("nav-button");
        syncAllButton.setOnAction(e -> ctx.getEventBus().publish("sync.full", null));

        HBox buttons = new HBox(10, syncButton, syncAllButton);
        root = new VBox(12, titleLabel, statusLabel, buttons);
        root.setPadding(new Insets(16));

        refreshView();
    }

    private void refreshView() {
        if (statusLabel == null) return;
        try {
            Platform.runLater(() -> {
                Optional<Instant> last = ctx.getDb().syncState().get()
                        .map(SyncState::lastSyncedAt);
                int unsynced = ctx.getDb().syncChangelog().findUnsynced().size();
                int conflicts = ctx.getDb().pendingConflicts().findAll().size();

                String lastStr = last.map(this::formatInstant).orElse(t("sync.never"));
                statusLabel.setText(t("sync.status", lastStr) + "\n"
                        + t("sync.pending", unsynced));
                if (conflicts > 0) {
                    statusLabel.setText(statusLabel.getText() + "\n"
                            + t("sync.conflicts", conflicts));
                    syncButton.setDisable(true);
                    syncAllButton.setDisable(true);
                    syncButton.setText(t("sync.blocked"));
                } else {
                    syncButton.setDisable(false);
                    syncAllButton.setDisable(false);
                    syncButton.setText(t("sync.now"));
                }
            });
        } catch (IllegalStateException e) {
            // JavaFX not running (headless/test)
        }
    }

    private String formatInstant(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(java.util.Locale.forLanguageTag(
                        bundle.getLocale().getLanguage()))
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    // ── Orchestration ─────────────────────────────────────────────────────────

    private void doSync() {
        try {
            push();
            pull();
            ctx.getEventBus().publish("sync.completed", null);
        } catch (Exception e) {
            ctx.getEventBus().publish("sync.failed", e.getMessage());
        }
    }

    private void doFullSync() {
        try {
            push();
            pullFromEpoch();
            ctx.getEventBus().publish("sync.completed", null);
        } catch (Exception e) {
            ctx.getEventBus().publish("sync.failed", e.getMessage());
        }
    }

    // ── Push (local → server) ────────────────────────────────────────────────

    private void push() throws Exception {
        // Build updates from dirty entities. For now: incidents only.
        List<Incident> dirtyIncidents = ctx.getDb().incidents().findDirty();
        if (dirtyIncidents.isEmpty()) return;

        ObjectNode body = mapper.createObjectNode();
        body.put("jobId", UUID.randomUUID().toString());

        ArrayNode updates = mapper.createArrayNode();
        for (Incident i : dirtyIncidents) {
            ObjectNode item = mapper.createObjectNode();
            item.put("entity_type", "incident");
            item.put("entity_id", i.id());

            // Only send the fields we track offline (title, description, severity, status).
            ObjectNode changes = mapper.createObjectNode();
            if (i.title() != null) changes.put("title", i.title());
            if (i.description() != null) changes.put("description", i.description());
            if (i.severity() != null) changes.put("severity", i.severity().name());
            if (i.status() != null) changes.put("status", i.status().name());
            item.set("changes", changes);

            // CDC: base_updated_at = server timestamp from last snapshot, NOT local clock.
            item.put("base_updated_at",
                    i.baseUpdatedAt() != null ? i.baseUpdatedAt() : "1970-01-01T00:00:00Z");

            updates.add(item);
        }
        body.set("updates", updates);

        String responseJson = ctx.getHttpClient().post("/sync/updates",
                mapper.writeValueAsString(body));
        JsonNode response = mapper.readTree(responseJson);

        // Index dirty incidents by ID for fast lookup.
        Map<String, Incident> byId = new HashMap<>();
        for (Incident i : dirtyIncidents) byId.put(i.id(), i);

        // Process per-entity results from the server.
        JsonNode results = response.path("results");
        if (results.isArray()) {
            for (JsonNode r : results) {
                String entityId = r.path("entity_id").asText();
                String status = r.path("status").asText();
                Incident local = byId.get(entityId);

                if ("applied".equals(status) && local != null) {
                    // Server accepted the change — mark clean.
                    Incident clean = new Incident(
                            local.id(), local.reporterId(), local.assignedTo(),
                            local.neighbourhoodId(), local.mongoDocumentId(),
                            local.title(), local.description(),
                            local.severity(), local.status(),
                            local.assignedAt(), local.createdAt(),
                            local.updatedAt(), local.resolvedAt(),
                            // update base_updated_at to current server time
                            response.path("sync_at").asText(local.baseUpdatedAt()),
                            Instant.now(),   // syncedAt
                            false);          // isDirty
                    ctx.getDb().incidents().save(clean);

                } else if ("conflict".equals(status) && local != null) {
                    // Server detected a conflict — record for UI resolution.
                    JsonNode conflict = r.path("conflict");
                    String fieldName = conflict.path("field_name").asText(null);
                    try {
                        String clientData = mapper.writeValueAsString(conflict.path("client_data"));
                        String serverData = mapper.writeValueAsString(conflict.path("server_data"));
                        ctx.getDb().pendingConflicts().save(new PendingConflict(
                                0, "incidents", entityId, fieldName,
                                clientData, serverData, Instant.now()));
                    } catch (Exception ignored) {}
                    // Keep is_dirty = 1 so the user can re-push after resolution.
                }
                // "skipped" = no action needed
            }
        }

        boolean hasConflicts = response.path("has_conflicts").asBoolean(false);
        if (!hasConflicts) {
            // All clean — safe to clear changelog.
            ctx.getDb().syncChangelog().markAllSynced();
        }
    }

    // ── Pull (server → local) ────────────────────────────────────────────────

    private void pull() throws Exception {
        Instant lastSync = ctx.getDb().syncState().get()
                .map(s -> s.lastSyncedAt().minusSeconds(30))  // 30s overlap
                .orElse(Instant.parse("1970-01-01T00:00:00Z"));
        pullSince(lastSync);
    }

    private void pullFromEpoch() throws Exception {
        pullSince(Instant.parse("1970-01-01T00:00:00Z"));
    }

    private void pullSince(Instant since) throws Exception {
        String baseUrl = "/sync/snapshot?since=" + since.toString() + "&limit=500";

        Instant pageSyncAt = null;
        String cursor = null;
        boolean hasMore;

        do {
            String url = baseUrl + (cursor != null ? "&cursor=" + cursor : "");
            String responseJson = ctx.getHttpClient().get(url);
            JsonNode root = mapper.readTree(responseJson);

            int incCount = root.path("incidents").size();
            int usrCount = root.path("users_raw").size();
            int lcCount = root.path("listing_categories").size();
            int ecCount = root.path("event_categories").size();
            System.out.println("[Sync] Pull page: incidents=" + incCount
                    + " users=" + usrCount + " listingCat=" + lcCount + " eventCat=" + ecCount);

            processIncidents(root.path("incidents"));
            processUsersRaw(root.path("users_raw"));
            processCategories(root.path("listing_categories"), "listing");
            processCategories(root.path("event_categories"), "event");

            cursor = root.path("cursor").asText(null);
            hasMore = root.path("has_more").asBoolean(false);

            String syncAtStr = root.path("sync_at").asText(null);
            if (syncAtStr != null) pageSyncAt = Instant.parse(syncAtStr);

        } while (hasMore);

        if (pageSyncAt != null) {
            ctx.getDb().syncState().save(new SyncState(pageSyncAt, null, false));
        }
    }

    // ── Pull: incident processor ──────────────────────────────────────────────

    private void processIncidents(JsonNode arr) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode remote : arr) {
            String id = remote.path("id").asText();
            // CDC: camelCase field names
            String remoteUpdatedAt = remote.path("updatedAt").asText(null);

            Optional<Incident> localOpt = ctx.getDb().incidents().findById(id);

            if (localOpt.isEmpty()) {
                // New incident from server — insert locally.
                upsertIncident(remote, remoteUpdatedAt);
                continue;
            }

            Incident local = localOpt.get();

            // Auto-cleanup stale conflicts before applying remote data.
            autoCleanStaleConflicts("incidents", id, remote);

            // If local is_dirty, skip overwrite — the push phase will handle it.
            // Otherwise, apply remote data (remote is source of truth for read-only entities).
            if (!local.isDirty()) {
                upsertIncident(remote, remoteUpdatedAt);
            }
            // else: local has pending changes → keep local, push will resolve.
        }
    }

    // ── Pull: user processor ─────────────────────────────────────────────────

    private void processUsersRaw(JsonNode arr) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode remote : arr) {
            String id = remote.path("id").asText();
            // Upsert into local users table (name, role, neighbourhood).
            // The local users table stores first_name, last_name, role, neighbourhood_id.
            try {
                var existing = ctx.getDb().users().findById(id);
                String firstName = remote.path("firstName").asText("");
                String lastName = remote.path("lastName").asText("");
                String role = remote.path("role").asText("resident");
                String neighbourhoodId = remote.path("neighbourhoodId").asText(null);
                boolean deleted = !remote.path("deletedAt").asText().isBlank();

                if (deleted) {
                    // Mark local user as deleted.
                    if (existing.isPresent()) {
                        var u = existing.get();
                        var updated = new tech.nabor.api.model.user.User(
                                u.id(), u.firstName(), u.lastName(), u.email(),
                                u.passwordHash(), u.totpSecret(), u.stripeAccountId(),
                                u.neighbourhoodId(), u.visibility(), u.bio(),
                                u.messagePolicy(), u.locale(),
                                u.profilePictureMongoId(), u.bannerMongoId(),
                                u.role(), u.lastLoginAt(), u.passwordChangedAt(),
                                u.createdAt(), u.updatedAt(), Instant.now());
                        ctx.getDb().users().save(updated);
                    }
                } else if (existing.isPresent()) {
                    var u = existing.get();
                    var updated = new tech.nabor.api.model.user.User(
                            u.id(), firstName, lastName, u.email(),
                            u.passwordHash(), u.totpSecret(), u.stripeAccountId(),
                            neighbourhoodId, u.visibility(), u.bio(),
                            u.messagePolicy(), u.locale(),
                            u.profilePictureMongoId(), u.bannerMongoId(),
                            tech.nabor.api.model.enums.UserRole.valueOf(
                                    role.replace("neighbourhood_rep", "neighbourhood_rep")),
                            u.lastLoginAt(), u.passwordChangedAt(),
                            u.createdAt(), Instant.now(), u.deletedAt());
                    ctx.getDb().users().save(updated);
                } else {
                    // New user — insert minimal record.
                    var newUser = new tech.nabor.api.model.user.User(
                            id, firstName, lastName, id + "@nabor.local",
                            "", null, null,
                            neighbourhoodId,
                            tech.nabor.api.model.enums.Visibility.public_,
                            null, tech.nabor.api.model.enums.MessagePolicy.open,
                            "fr", null, null,
                            tech.nabor.api.model.enums.UserRole.valueOf(role),
                            null, null, Instant.now(), null, null);
                    ctx.getDb().users().save(newUser);
                }
            } catch (Exception e) {
                System.err.println("[Sync] processUsersRaw failed: " + e.getMessage());
            }
        }
    }

    // ── Pull: category processor ─────────────────────────────────────────────

    private void processCategories(JsonNode arr, String domain) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode remote : arr) {
            try {
                int id = remote.path("id").asInt();
                String name = remote.path("categoryName").asText("");
                int parent = remote.path("parentCategoryId").asInt(-1);

                if ("listing".equals(domain)) {
                    ctx.getDb().listingCategories().save(
                            new tech.nabor.api.model.listings.ListingCategory(
                                    id, parent > 0 ? parent : null, name,
                                    Instant.now(), null));
                } else {
                    ctx.getDb().evenementCategories().save(
                            new tech.nabor.api.model.events.EvenementCategory(
                                    id, parent > 0 ? parent : null, name,
                                    Instant.now(), null));
                }
            } catch (Exception e) {
                System.err.println("[Sync] processCategories(" + domain + ") failed: " + e.getMessage());
            }
        }
    }

    // ── Stale conflict auto-cleanup ──────────────────────────────────────────

    /**
     * Checks if any pending conflicts for this entity are now stale
     * (the server data changed again, resolving the conflict implicitly).
     * Auto-removes stale conflicts so the user isn't bothered.
     */
    private void autoCleanStaleConflicts(String table, String rowId, JsonNode currentRemote) {
        List<PendingConflict> existing = ctx.getDb().pendingConflicts()
                .findByRow(table, rowId);
        for (PendingConflict c : existing) {
            boolean stale = isConflictStale(c, currentRemote);
            if (stale) {
                ctx.getDb().pendingConflicts().delete(c.id());
                ctx.getDb().resolvedConflicts().save(new ResolvedConflict(
                        0, c.tableName(), c.rowId(), c.fieldName(),
                        "auto", Instant.now()));
            }
        }
    }

    private boolean isConflictStale(PendingConflict conflict, JsonNode currentRemote) {
        try {
            JsonNode clientData = mapper.readTree(conflict.localValue());
            JsonNode serverData = mapper.readTree(conflict.remoteValue());

            String field = conflict.fieldName();
            if (field != null && !field.isBlank()) {
                // Single-field conflict: compare the field value.
                String currentVal = currentRemote.path(field).asText(null);
                if (currentVal == null) return false;

                String localVal = clientData.path(field).asText(null);
                String remoteVal = serverData.path(field).asText(null);

                // If current matches either side, conflict is resolved.
                return currentVal.equals(localVal) || currentVal.equals(remoteVal);
            } else {
                // Whole-record conflict (field_name = null): check if any field
                // from the conflict still differs from current remote.
                var fieldNames = clientData.fieldNames();
                while (fieldNames.hasNext()) {
                    String fn = fieldNames.next();
                    String localVal = clientData.path(fn).asText(null);
                    String remoteVal = serverData.path(fn).asText(null);
                    String currentVal = currentRemote.path(fn).asText(null);
                    if (currentVal == null) continue;
                    // If current doesn't match either, conflict is still live.
                    if (!currentVal.equals(localVal) && !currentVal.equals(remoteVal)) {
                        return false;
                    }
                }
                return true; // all fields resolved one way or another
            }
        } catch (Exception e) {
            return false; // can't parse → assume still valid
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void upsertIncident(JsonNode remote, String baseUpdatedAt) {
        try {
            String id = remote.path("id").asText();
            Incident incident = new Incident(
                    id,
                    remote.path("reporterId").asText(null),
                    remote.path("assignedTo").asText(null),
                    remote.path("neighbourhoodId").asText(null),
                    remote.path("mongoDocumentId").asText(null),
                    remote.path("title").asText(""),
                    remote.path("description").asText(null),
                    parseSeverity(remote.path("severity").asText(null)),
                    parseStatus(remote.path("status").asText(null)),
                    parseInstant(remote, "assignedAt"),
                    parseInstant(remote, "createdAt"),
                    parseInstant(remote, "updatedAt"),
                    parseInstant(remote, "resolvedAt"),
                    baseUpdatedAt,  // set from remote.updatedAt
                    null,           // syncedAt = null (pulled, not pushed)
                    false);         // isDirty = false (clean copy from server)
            ctx.getDb().incidents().save(incident);
        } catch (Exception e) {
            System.err.println("[Sync] upsertIncident failed for " + remote.path("id").asText("?") + ": " + e.getMessage());
        }
    }

    private IncidentSeverity parseSeverity(String s) {
        if (s == null) return IncidentSeverity.medium;
        try { return IncidentSeverity.valueOf(s); }
        catch (IllegalArgumentException e) { return IncidentSeverity.medium; }
    }

    private IncidentStatus parseStatus(String s) {
        if (s == null) return IncidentStatus.open;
        try { return IncidentStatus.valueOf(s); }
        catch (IllegalArgumentException e) { return IncidentStatus.open; }
    }

    private Instant parseInstant(JsonNode node, String field) {
        if (node == null) return null;
        String text = node.path(field).asText(null);
        if (text == null || text.isBlank()) return null;
        try { return Instant.parse(text); }
        catch (Exception e) { return null; }
    }
}
