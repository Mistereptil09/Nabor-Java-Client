// core-impl/src/main/java/tech/nabor/app/db/repository/user/AppUserSessionRepository.java
package tech.nabor.app.db.repository.user;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.user.UserSession;
import tech.nabor.api.repository.user.UserSessionRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppUserSessionRepository implements UserSessionRepository {

    private final Jdbi jdbi;

    public AppUserSessionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class UserSessionMapper implements RowMapper<UserSession> {
        @Override
        public UserSession map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserSession(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("refresh_token_hash"),
                    rs.getString("device_name"),
                    rs.getString("ip_address"),
                    rs.getString("user_agent"),
                    InstantMapper.fromNullableLong(rs, "last_used_at"),
                    InstantMapper.fromNullableLong(rs, "expires_at"),
                    InstantMapper.fromNullableLong(rs, "revoked_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<UserSession> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM user_sessions WHERE id = :id")
                        .bind("id", id)
                        .map(new UserSessionMapper())
                        .findOne()
        );
    }

    @Override
    public Optional<UserSession> findByTokenHash(String hash) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM user_sessions WHERE refresh_token_hash = :hash")
                        .bind("hash", hash)
                        .map(new UserSessionMapper())
                        .findOne()
        );
    }

    @Override
    public List<UserSession> findActiveByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM user_sessions
                WHERE user_id = :userId AND revoked_at IS NULL
                """)
                        .bind("userId", userId)
                        .map(new UserSessionMapper())
                        .list()
        );
    }

    @Override
    public void save(UserSession session) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO user_sessions (
                    id, user_id, refresh_token_hash, device_name,
                    ip_address, user_agent, last_used_at, expires_at, revoked_at
                ) VALUES (
                    :id, :userId, :refreshTokenHash, :deviceName,
                    :ipAddress, :userAgent, :lastUsedAt, :expiresAt, :revokedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    refresh_token_hash = excluded.refresh_token_hash,
                    device_name        = excluded.device_name,
                    ip_address         = excluded.ip_address,
                    user_agent         = excluded.user_agent,
                    last_used_at       = excluded.last_used_at,
                    expires_at         = excluded.expires_at,
                    revoked_at         = excluded.revoked_at
                """)
                        .bind("id",               session.id())
                        .bind("userId",           session.userId())
                        .bind("refreshTokenHash", session.refreshTokenHash())
                        .bind("deviceName",       session.deviceName())
                        .bind("ipAddress",        session.ipAddress())
                        .bind("userAgent",        session.userAgent())
                        .bind("lastUsedAt",       InstantMapper.toLong(session.lastUsedAt()))
                        .bind("expiresAt",        InstantMapper.toLong(session.expiresAt()))
                        .bind("revokedAt",        InstantMapper.toLong(session.revokedAt()))
                        .execute()
        );
    }

    @Override
    public void revoke(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE user_sessions SET revoked_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }

    @Override
    public void revokeAllForUser(String userId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE user_sessions SET revoked_at = :now
                WHERE user_id = :userId AND revoked_at IS NULL
                """)
                        .bind("now",    System.currentTimeMillis())
                        .bind("userId", userId)
                        .execute()
        );
    }
}