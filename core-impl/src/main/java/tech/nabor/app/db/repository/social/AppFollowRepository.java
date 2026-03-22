package tech.nabor.app.db.repository.social;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.social.Follow;
import tech.nabor.api.repository.social.FollowRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppFollowRepository implements FollowRepository {

    private final Jdbi jdbi;

    public AppFollowRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class FollowMapper implements RowMapper<Follow> {
        @Override
        public Follow map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Follow(
                    rs.getString("follower_id"),
                    rs.getString("followed_id"),
                    InstantMapper.fromNullableLong(rs, "followed_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<Follow> findFollowersByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM follow WHERE followed_id = :userId")
                        .bind("userId", userId)
                        .map(new FollowMapper())
                        .list()
        );
    }

    @Override
    public List<Follow> findFollowingByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM follow WHERE follower_id = :userId")
                        .bind("userId", userId)
                        .map(new FollowMapper())
                        .list()
        );
    }

    @Override
    public boolean isFollowing(String followerId, String followedId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT COUNT(*) FROM follow
                WHERE follower_id = :followerId AND followed_id = :followedId
                """)
                        .bind("followerId", followerId)
                        .bind("followedId", followedId)
                        .mapTo(Integer.class)
                        .one() > 0
        );
    }

    @Override
    public void save(Follow follow) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO follow (follower_id, followed_id, followed_at)
                VALUES (:followerId, :followedId, :followedAt)
                ON CONFLICT(follower_id, followed_id) DO UPDATE SET
                    followed_at = excluded.followed_at
                """)
                        .bind("followerId", follow.followerId())
                        .bind("followedId", follow.followedId())
                        .bind("followedAt", InstantMapper.toLong(follow.followedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String followerId, String followedId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                DELETE FROM follow
                WHERE follower_id = :followerId AND followed_id = :followedId
                """)
                        .bind("followerId", followerId)
                        .bind("followedId", followedId)
                        .execute()
        );
    }
}