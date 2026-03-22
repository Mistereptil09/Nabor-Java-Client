package tech.nabor.app.db.repository.social;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.social.Friendship;
import tech.nabor.api.repository.social.FriendshipRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppFriendshipRepository implements FriendshipRepository {

    private final Jdbi jdbi;

    public AppFriendshipRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class FriendshipMapper implements RowMapper<Friendship> {
        @Override
        public Friendship map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Friendship(
                    rs.getString("id"),
                    rs.getString("user1_id"),
                    rs.getString("user2_id"),
                    InstantMapper.fromNullableLong(rs, "friended_at"),
                    InstantMapper.fromNullableLong(rs, "unfriended_at"),
                    rs.getString("group_id")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<Friendship> findByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM friendships
                WHERE (user1_id = :userId OR user2_id = :userId)
                AND unfriended_at IS NULL
                """)
                        .bind("userId", userId)
                        .map(new FriendshipMapper())
                        .list()
        );
    }

    @Override
    public Optional<Friendship> findByPair(String user1Id, String user2Id) {
        // respecte la convention user1_id < user2_id
        String a = user1Id.compareTo(user2Id) < 0 ? user1Id : user2Id;
        String b = user1Id.compareTo(user2Id) < 0 ? user2Id : user1Id;

        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM friendships
                WHERE user1_id = :a AND user2_id = :b
                """)
                        .bind("a", a)
                        .bind("b", b)
                        .map(new FriendshipMapper())
                        .findOne()
        );
    }

    @Override
    public boolean areFriends(String user1Id, String user2Id) {
        return findByPair(user1Id, user2Id)
                .map(f -> f.unfriendedAt() == null)
                .orElse(false);
    }

    @Override
    public void save(Friendship friendship) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO friendships (id, user1_id, user2_id, friended_at, unfriended_at, group_id)
                VALUES (:id, :user1Id, :user2Id, :friendedAt, :unfriendedAt, :groupId)
                ON CONFLICT(id) DO UPDATE SET
                    friended_at   = excluded.friended_at,
                    unfriended_at = excluded.unfriended_at,
                    group_id      = excluded.group_id
                """)
                        .bind("id",            friendship.id())
                        .bind("user1Id",       friendship.user1Id())
                        .bind("user2Id",       friendship.user2Id())
                        .bind("friendedAt",    InstantMapper.toLong(friendship.friendedAt()))
                        .bind("unfriendedAt",  InstantMapper.toLong(friendship.unfriendedAt()))
                        .bind("groupId",       friendship.groupId())
                        .execute()
        );
    }

    @Override
    public void delete(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE friendships SET unfriended_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}