package tech.nabor.plugin.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.nabor.api.PluginContext;

import java.time.Instant;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.listings.Listing;
import tech.nabor.api.model.listings.ListingModerationAction;
import tech.nabor.api.model.listings.ListingReport;
import tech.nabor.api.model.listings.ListingTransaction;
import tech.nabor.api.model.events.*;
import tech.nabor.api.model.messages.ChatGroup;
import tech.nabor.api.model.messages.UserInGroup;
import tech.nabor.api.model.polls.Poll;
import tech.nabor.api.model.polls.PollOption;
import tech.nabor.api.model.polls.Vote;
import tech.nabor.api.model.social.Follow;
import tech.nabor.api.model.social.Friendship;
import tech.nabor.api.model.user.User;
import tech.nabor.api.model.enums.*;

/**
 * Processes each entity type from a sync snapshot JSON payload.
 * Jackson deserializes JSON directly into Java records — no manual field mapping.
 * Server is source of truth; entities are always upserted on pull.
 */
class SnapshotProcessor {

    private final PluginContext ctx;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    SnapshotProcessor(PluginContext ctx) {
        this.ctx = ctx;
    }

    void processAllEntities(JsonNode root) {
        upsertArray(root, "incidents", Incident.class,
                i -> ctx.getDb().incidents().save(i));
        processUsers(root.path("users_raw"));
        processCategories(root.path("listing_categories"), "listing");
        processCategories(root.path("event_categories"), "event");
        processNeighbourhoods(root.path("neighbourhoods"));

        upsertArray(root, "listings", Listing.class,
                l -> { if (!isDeleted(l)) ctx.getDb().listings().save(l); });
        upsertArray(root, "events", Evenement.class,
                e -> { if (!isDeleted(e)) ctx.getDb().evenements().save(e); });
        upsertArray(root, "chat_groups", ChatGroup.class,
                c -> { if (!isDeleted(c)) ctx.getDb().chatGroups().save(c); });
        upsertArray(root, "polls", Poll.class,
                p -> { if (!isDeleted(p)) ctx.getDb().polls().save(p); });

        upsertArray(root, "poll_options", PollOption.class,
                o -> { if (ctx.getDb().pollOptions().findById(o.id()).isEmpty()) ctx.getDb().pollOptions().save(o); });
        upsertArray(root, "votes", Vote.class,
                v -> { if (ctx.getDb().votes().findByUserAndOption(v.userId(), v.optionId()).isEmpty()) ctx.getDb().votes().save(v); });
        upsertArray(root, "event_participants", EventParticipant.class,
                p -> { if (ctx.getDb().eventParticipants().findByUserAndEvent(p.userId(), p.eventId()).isEmpty()) ctx.getDb().eventParticipants().save(p); });
        upsertArray(root, "users_in_group", UserInGroup.class,
                u -> { if (ctx.getDb().usersInGroup().findByUserAndGroup(u.userId(), u.groupId()).isEmpty()) ctx.getDb().usersInGroup().save(u); });

        upsertArray(root, "follows", Follow.class,
                f -> { if (!ctx.getDb().follows().isFollowing(f.followerId(), f.followedId())) ctx.getDb().follows().save(f); });
        upsertArray(root, "friendships", Friendship.class,
                f -> ctx.getDb().friendships().save(f));
        upsertArray(root, "listing_transactions", ListingTransaction.class,
                t -> { if (ctx.getDb().listingTransactions().findById(t.id()).isEmpty()) ctx.getDb().listingTransactions().save(t); });

        upsertArray(root, "listing_reports", ListingReport.class,
                r -> ctx.getDb().listingReports().save(r));
        upsertArray(root, "event_reports", EventReport.class,
                r -> ctx.getDb().eventReports().save(r));
        upsertArray(root, "listing_moderation_actions", ListingModerationAction.class,
                a -> ctx.getDb().listingModerationActions().save(a));
        upsertArray(root, "event_moderation_actions", EventModerationAction.class,
                a -> ctx.getDb().eventModerationActions().save(a));
    }

    // ── Generic array processor ─────────────────────────────────────────────

    private <T> void upsertArray(JsonNode root, String field, Class<T> type,
                                  java.util.function.Consumer<T> saveFn) {
        JsonNode arr = root.path(field);
        if (arr == null || !arr.isArray()) return;
        int n = 0;
        for (JsonNode node : arr) {
            try {
                T entity = mapper.treeToValue(node, type);
                saveFn.accept(entity);
                n++;
            } catch (Exception e) {
                System.out.println("[Sync] " + field + " FAIL: " + e.getMessage());
            }
        }
        if (n > 0) System.out.println("[Sync]   " + field + ": " + n);
    }

    // ── Users (special: field-level mapping + full User record) ─────────────

    private void processUsers(JsonNode arr) {
        if (arr == null || !arr.isArray()) return;
        int n = 0;
        for (JsonNode r : arr) {
            try {
                String id = r.path("id").asText();
                String firstName = r.path("firstName").asText("");
                String lastName = r.path("lastName").asText("");
                String email = r.path("email").asText("");
                String role = r.path("role").asText("resident");
                String nid = r.path("neighbourhoodId").asText(null);
                String bio = r.path("bio").asText(null);
                Visibility visibility = parseVisibility(r.path("visibility").asText("public"));
                MessagePolicy msgPolicy = parseMessagePolicy(r.path("messagePolicy").asText("open"));
                String locale = r.path("locale").asText("fr");
                Instant updatedAt = parseInstant(r, "updatedAt");
                boolean deleted = !r.path("deletedAt").asText("").isBlank();

                var existing = ctx.getDb().users().findById(id);
                if (deleted && existing.isPresent()) {
                    var u = existing.get();
                    ctx.getDb().users().save(new User(u.id(), u.firstName(), u.lastName(), u.email(),
                            u.passwordHash(), u.totpSecret(), u.stripeAccountId(),
                            u.neighbourhoodId(), visibility, bio,
                            msgPolicy, locale,
                            u.profilePictureMongoId(), u.bannerMongoId(),
                            u.role(), u.lastLoginAt(), u.passwordChangedAt(),
                            u.createdAt(), updatedAt, java.time.Instant.now()));
                } else if (!deleted && existing.isPresent()) {
                    var u = existing.get();
                    String userEmail = email.isEmpty() ? u.email() : email;
                    ctx.getDb().users().save(new User(u.id(), firstName, lastName, userEmail,
                            u.passwordHash(), u.totpSecret(), u.stripeAccountId(),
                            nid, visibility, bio, msgPolicy, locale,
                            u.profilePictureMongoId(), u.bannerMongoId(),
                            UserRole.valueOf(role), u.lastLoginAt(), u.passwordChangedAt(),
                            u.createdAt(), updatedAt, u.deletedAt()));
                } else if (!deleted) {
                    ctx.getDb().users().save(new User(id, firstName, lastName, email,
                            "", null, null, nid, visibility, bio,
                            msgPolicy, locale, null, null,
                            UserRole.valueOf(role), null, null,
                            java.time.Instant.now(), updatedAt, null));
                }
                n++;
            } catch (Exception e) {
                System.out.println("[Sync] users FAIL: " + e.getMessage());
            }
        }
        if (n > 0) System.out.println("[Sync]   users_raw: " + n);
    }

    // ── Categories ──────────────────────────────────────────────────────────

    private void processCategories(JsonNode arr, String domain) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode r : arr) {
            try {
                int id = r.path("id").asInt();
                String name = r.path("categoryName").asText("");
                int parent = r.path("parentCategoryId").asInt(-1);
                if ("listing".equals(domain)) {
                    ctx.getDb().listingCategories().save(
                            new tech.nabor.api.model.listings.ListingCategory(
                                    id, parent > 0 ? parent : null, name, java.time.Instant.now(), null));
                } else {
                    ctx.getDb().evenementCategories().save(
                            new tech.nabor.api.model.events.EvenementCategory(
                                    id, parent > 0 ? parent : null, name, java.time.Instant.now(), null));
                }
            } catch (Exception e) {
                System.out.println("[Sync] categories FAIL: " + e.getMessage());
            }
        }
    }

    // ── Neighbourhoods ──────────────────────────────────────────────────────

    private void processNeighbourhoods(JsonNode node) {
        if (node == null || !node.isObject()) return;
        var fields = node.fields();
        while (fields.hasNext()) {
            var e = fields.next();
            try { ctx.getDb().mappingNeighbourhoods().upsert(e.getKey(), e.getValue().asText()); }
            catch (Exception ex) { System.out.println("[Sync] neighbourhoods FAIL: " + ex.getMessage()); }
        }
    }

    // ── Deleted check for entities with deletedAt field ─────────────────────

    private static boolean isDeleted(Listing l) { return l.deletedAt() != null; }
    private static boolean isDeleted(Evenement e) { return e.deletedAt() != null; }
    private static boolean isDeleted(ChatGroup c) { return c.deletedAt() != null; }
    private static boolean isDeleted(Poll p) { return p.deletedAt() != null; }

    private static Visibility parseVisibility(String s) {
        try { return Visibility.valueOf(s); }
        catch (Exception e) { return Visibility.public_; }
    }

    private static MessagePolicy parseMessagePolicy(String s) {
        try { return MessagePolicy.valueOf(s); }
        catch (Exception e) { return MessagePolicy.open; }
    }

    private static Instant parseInstant(JsonNode node, String field) {
        if (node == null) return null;
        String text = node.path(field).asText(null);
        if (text == null || text.isBlank()) return null;
        try { return Instant.parse(text); } catch (Exception e) { return null; }
    }
}
