package tech.nabor.app.db.repository.social;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.social.UserBlock;
import tech.nabor.api.repository.social.UserBlockRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppUserBlockRepository implements UserBlockRepository {

    private final Jdbi jdbi;

    public AppUserBlockRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class UserBlockMapper implements RowMapper<UserBlock> {
        @Override
        public UserBlock map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserBlock(
                    rs.getString("blocker_id"),
                    rs.getString("blocked_id"),
                    InstantMapper.fromNullableLong(rs, "blocked_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<UserBlock> findByBlockerId(String blockerId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM user_blocks WHERE blocker_id = :blockerId")
                        .bind("blockerId", blockerId)
                        .map(new UserBlockMapper())
                        .list()
        );
    }

    @Override
    public boolean isBlocked(String blockerId, String blockedId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT COUNT(*) FROM user_blocks
                WHERE blocker_id = :blockerId AND blocked_id = :blockedId
                """)
                        .bind("blockerId", blockerId)
                        .bind("blockedId", blockedId)
                        .mapTo(Integer.class)
                        .one() > 0
        );
    }

    @Override
    public void save(UserBlock block) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO user_blocks (blocker_id, blocked_id, blocked_at)
                VALUES (:blockerId, :blockedId, :blockedAt)
                ON CONFLICT(blocker_id, blocked_id) DO UPDATE SET
                    blocked_at = excluded.blocked_at
                """)
                        .bind("blockerId", block.blockerId())
                        .bind("blockedId", block.blockedId())
                        .bind("blockedAt", InstantMapper.toLong(block.blockedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String blockerId, String blockedId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                DELETE FROM user_blocks
                WHERE blocker_id = :blockerId AND blocked_id = :blockedId
                """)
                        .bind("blockerId", blockerId)
                        .bind("blockedId", blockedId)
                        .execute()
        );
    }
}