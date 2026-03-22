package tech.nabor.app.db.repository.listings;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.listings.ListingModerationAction;
import tech.nabor.api.model.enums.ModerationAction;
import tech.nabor.api.repository.listings.ListingModerationActionRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppListingModerationActionRepository implements ListingModerationActionRepository {

    private final Jdbi jdbi;

    public AppListingModerationActionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class ListingModerationActionMapper implements RowMapper<ListingModerationAction> {
        @Override
        public ListingModerationAction map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ListingModerationAction(
                    rs.getString("id"),
                    rs.getString("listing_id"),
                    rs.getString("moderator_id"),
                    ModerationAction.valueOf(rs.getString("action")),
                    rs.getString("reason"),
                    InstantMapper.fromNullableLong(rs, "created_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<ListingModerationAction> findByListingId(String listingId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM listing_moderation_actions
                WHERE listing_id = :listingId ORDER BY created_at DESC
                """)
                        .bind("listingId", listingId)
                        .map(new ListingModerationActionMapper())
                        .list()
        );
    }

    @Override
    public List<ListingModerationAction> findByModeratorId(String moderatorId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM listing_moderation_actions
                WHERE moderator_id = :moderatorId ORDER BY created_at DESC
                """)
                        .bind("moderatorId", moderatorId)
                        .map(new ListingModerationActionMapper())
                        .list()
        );
    }

    @Override
    public void save(ListingModerationAction action) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO listing_moderation_actions
                    (id, listing_id, moderator_id, action, reason, created_at)
                VALUES
                    (:id, :listingId, :moderatorId, :action, :reason, :createdAt)
                """)
                        .bind("id",          action.id())
                        .bind("listingId",   action.listingId())
                        .bind("moderatorId", action.moderatorId())
                        .bind("action",      action.action().name())
                        .bind("reason",      action.reason())
                        .bind("createdAt",   InstantMapper.toLong(action.createdAt()))
                        .execute()
        );
    }
}