package tech.nabor.app.db.repository.messages;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.messages.UserInGroup;
import tech.nabor.api.model.enums.GroupRole;
import tech.nabor.api.repository.messages.UserInGroupRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppUserInGroupRepository implements UserInGroupRepository {

    private final Jdbi jdbi;

    public AppUserInGroupRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class UserInGroupMapper implements RowMapper<UserInGroup> {
        @Override
        public UserInGroup map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserInGroup(
                    rs.getString("user_id"),
                    rs.getString("group_id"),
                    GroupRole.valueOf(rs.getString("role_in_group")),
                    InstantMapper.fromNullableLong(rs, "joined_at"),
                    InstantMapper.fromNullableLong(rs, "left_at"),
                    InstantMapper.fromNullableLong(rs, "kicked_at"),
                    rs.getInt("is_muted") == 1,
                    InstantMapper.fromNullableLong(rs, "muted_until")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<UserInGroup> findByGroupId(String groupId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM users_in_group
                WHERE group_id = :groupId AND left_at IS NULL AND kicked_at IS NULL
                """)
                        .bind("groupId", groupId)
                        .map(new UserInGroupMapper())
                        .list()
        );
    }

    @Override
    public List<UserInGroup> findByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM users_in_group
                WHERE user_id = :userId AND left_at IS NULL AND kicked_at IS NULL
                """)
                        .bind("userId", userId)
                        .map(new UserInGroupMapper())
                        .list()
        );
    }

    @Override
    public Optional<UserInGroup> findByUserAndGroup(String userId, String groupId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM users_in_group
                WHERE user_id = :userId AND group_id = :groupId
                """)
                        .bind("userId",  userId)
                        .bind("groupId", groupId)
                        .map(new UserInGroupMapper())
                        .findOne()
        );
    }

    @Override
    public boolean isMember(String userId, String groupId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT COUNT(*) FROM users_in_group
                WHERE user_id = :userId AND group_id = :groupId
                AND left_at IS NULL AND kicked_at IS NULL
                """)
                        .bind("userId",  userId)
                        .bind("groupId", groupId)
                        .mapTo(Integer.class)
                        .one() > 0
        );
    }

    @Override
    public void save(UserInGroup userInGroup) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO users_in_group
                    (user_id, group_id, role_in_group, joined_at, left_at, kicked_at, is_muted, muted_until)
                VALUES
                    (:userId, :groupId, :roleInGroup, :joinedAt, :leftAt, :kickedAt, :isMuted, :mutedUntil)
                ON CONFLICT(user_id, group_id) DO UPDATE SET
                    role_in_group = excluded.role_in_group,
                    left_at       = excluded.left_at,
                    kicked_at     = excluded.kicked_at,
                    is_muted      = excluded.is_muted,
                    muted_until   = excluded.muted_until
                """)
                        .bind("userId",      userInGroup.userId())
                        .bind("groupId",     userInGroup.groupId())
                        .bind("roleInGroup", userInGroup.roleInGroup().name())
                        .bind("joinedAt",    InstantMapper.toLong(userInGroup.joinedAt()))
                        .bind("leftAt",      InstantMapper.toLong(userInGroup.leftAt()))
                        .bind("kickedAt",    InstantMapper.toLong(userInGroup.kickedAt()))
                        .bind("isMuted",     userInGroup.isMuted() ? 1 : 0)
                        .bind("mutedUntil",  InstantMapper.toLong(userInGroup.mutedUntil()))
                        .execute()
        );
    }

    @Override
    public void leave(String userId, String groupId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE users_in_group SET left_at = :now
                WHERE user_id = :userId AND group_id = :groupId
                """)
                        .bind("now",     System.currentTimeMillis())
                        .bind("userId",  userId)
                        .bind("groupId", groupId)
                        .execute()
        );
    }

    @Override
    public void kick(String userId, String groupId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE users_in_group SET kicked_at = :now
                WHERE user_id = :userId AND group_id = :groupId
                """)
                        .bind("now",     System.currentTimeMillis())
                        .bind("userId",  userId)
                        .bind("groupId", groupId)
                        .execute()
        );
    }
}