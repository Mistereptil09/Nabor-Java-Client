package tech.nabor.app.db.repository.events;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.events.EventModerationAction;
import tech.nabor.api.model.enums.ModerationAction;
import tech.nabor.api.repository.events.EventModerationActionRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppEventModerationActionRepository implements EventModerationActionRepository {

    private final Jdbi jdbi;

    public AppEventModerationActionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class EventModerationActionMapper implements RowMapper<EventModerationAction> {
        @Override
        public EventModerationAction map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new EventModerationAction(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    rs.getString("moderator_id"),
                    ModerationAction.valueOf(rs.getString("action")),
                    rs.getString("reason"),
                    InstantMapper.fromNullableLong(rs, "created_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<EventModerationAction> findByEventId(String eventId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM event_moderation_actions
                WHERE event_id = :eventId ORDER BY created_at DESC
                """)
                        .bind("eventId", eventId)
                        .map(new EventModerationActionMapper())
                        .list()
        );
    }

    @Override
    public List<EventModerationAction> findByModeratorId(String moderatorId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM event_moderation_actions
                WHERE moderator_id = :moderatorId ORDER BY created_at DESC
                """)
                        .bind("moderatorId", moderatorId)
                        .map(new EventModerationActionMapper())
                        .list()
        );
    }

    @Override
    public void save(EventModerationAction action) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO event_moderation_actions
                    (id, event_id, moderator_id, action, reason, created_at)
                VALUES
                    (:id, :eventId, :moderatorId, :action, :reason, :createdAt)
                """)
                        .bind("id",          action.id())
                        .bind("eventId",     action.eventId())
                        .bind("moderatorId", action.moderatorId())
                        .bind("action",      action.action().name())
                        .bind("reason",      action.reason())
                        .bind("createdAt",   InstantMapper.toLong(action.createdAt()))
                        .execute()
        );
    }
}