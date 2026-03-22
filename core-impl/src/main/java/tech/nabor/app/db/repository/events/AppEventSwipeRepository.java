package tech.nabor.app.db.repository.events;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.events.EventSwipe;
import tech.nabor.api.model.enums.SwipeDirection;
import tech.nabor.api.repository.events.EventSwipeRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppEventSwipeRepository implements EventSwipeRepository {

    private final Jdbi jdbi;

    public AppEventSwipeRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class EventSwipeMapper implements RowMapper<EventSwipe> {
        @Override
        public EventSwipe map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new EventSwipe(
                    rs.getString("user_id"),
                    rs.getString("event_id"),
                    SwipeDirection.valueOf(rs.getString("direction")),
                    InstantMapper.fromNullableLong(rs, "swiped_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<EventSwipe> findByUserAndEvent(String userId, String eventId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM event_swipes
                WHERE user_id = :userId AND event_id = :eventId
                """)
                        .bind("userId",  userId)
                        .bind("eventId", eventId)
                        .map(new EventSwipeMapper())
                        .findOne()
        );
    }

    @Override
    public List<EventSwipe> findByUserAndDirection(String userId, SwipeDirection direction) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM event_swipes
                WHERE user_id = :userId AND direction = :direction
                """)
                        .bind("userId",    userId)
                        .bind("direction", direction.name())
                        .map(new EventSwipeMapper())
                        .list()
        );
    }

    @Override
    public void save(EventSwipe swipe) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO event_swipes (user_id, event_id, direction, swiped_at)
                VALUES (:userId, :eventId, :direction, :swipedAt)
                ON CONFLICT(user_id, event_id) DO UPDATE SET
                    direction = excluded.direction,
                    swiped_at = excluded.swiped_at
                """)
                        .bind("userId",    swipe.userId())
                        .bind("eventId",   swipe.eventId())
                        .bind("direction", swipe.direction().name())
                        .bind("swipedAt",  InstantMapper.toLong(swipe.swipedAt()))
                        .execute()
        );
    }
}