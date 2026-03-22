package tech.nabor.app.db.repository.social;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.social.UserSwipe;
import tech.nabor.api.model.enums.SwipeDirection;
import tech.nabor.api.repository.social.UserSwipeRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppUserSwipeRepository implements UserSwipeRepository {

    private final Jdbi jdbi;

    public AppUserSwipeRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class UserSwipeMapper implements RowMapper<UserSwipe> {
        @Override
        public UserSwipe map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserSwipe(
                    rs.getString("swiper_id"),
                    rs.getString("swiped_id"),
                    SwipeDirection.valueOf(rs.getString("direction")),
                    InstantMapper.fromNullableLong(rs, "swiped_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<UserSwipe> findBySwiperAndDirection(String swiperId, SwipeDirection direction) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM user_swipes
                WHERE swiper_id = :swiperId AND direction = :direction
                """)
                        .bind("swiperId",  swiperId)
                        .bind("direction", direction.name())
                        .map(new UserSwipeMapper())
                        .list()
        );
    }

    @Override
    public Optional<UserSwipe> findByPair(String swiperId, String swipedId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM user_swipes
                WHERE swiper_id = :swiperId AND swiped_id = :swipedId
                """)
                        .bind("swiperId", swiperId)
                        .bind("swipedId", swipedId)
                        .map(new UserSwipeMapper())
                        .findOne()
        );
    }

    @Override
    public void save(UserSwipe swipe) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO user_swipes (swiper_id, swiped_id, direction, swiped_at)
                VALUES (:swiperId, :swipedId, :direction, :swipedAt)
                ON CONFLICT(swiper_id, swiped_id) DO UPDATE SET
                    direction = excluded.direction,
                    swiped_at = excluded.swiped_at
                """)
                        .bind("swiperId",  swipe.swiperId())
                        .bind("swipedId",  swipe.swipedId())
                        .bind("direction", swipe.direction().name())
                        .bind("swipedAt",  InstantMapper.toLong(swipe.swipedAt()))
                        .execute()
        );
    }
}