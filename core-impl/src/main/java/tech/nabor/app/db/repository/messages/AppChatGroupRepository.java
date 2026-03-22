package tech.nabor.app.db.repository.messages;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.messages.ChatGroup;
import tech.nabor.api.model.enums.ChatGroupType;
import tech.nabor.api.repository.messages.ChatGroupRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppChatGroupRepository implements ChatGroupRepository {

    private final Jdbi jdbi;

    public AppChatGroupRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class ChatGroupMapper implements RowMapper<ChatGroup> {
        @Override
        public ChatGroup map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ChatGroup(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("created_by"),
                    ChatGroupType.valueOf(rs.getString("type")),
                    rs.getString("listing_id"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at"),
                    InstantMapper.fromNullableLong(rs, "deleted_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<ChatGroup> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM chat_groups WHERE id = :id")
                        .bind("id", id)
                        .map(new ChatGroupMapper())
                        .findOne()
        );
    }

    @Override
    public List<ChatGroup> findByType(ChatGroupType type) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM chat_groups
                WHERE type = :type AND deleted_at IS NULL
                """)
                        .bind("type", type.name())
                        .map(new ChatGroupMapper())
                        .list()
        );
    }

    @Override
    public void save(ChatGroup group) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO chat_groups (id, name, description, created_by, type, listing_id, created_at, updated_at, deleted_at)
                VALUES (:id, :name, :description, :createdBy, :type, :listingId, :createdAt, :updatedAt, :deletedAt)
                ON CONFLICT(id) DO UPDATE SET
                    name        = excluded.name,
                    description = excluded.description,
                    type        = excluded.type,
                    listing_id  = excluded.listing_id,
                    updated_at  = excluded.updated_at,
                    deleted_at  = excluded.deleted_at
                """)
                        .bind("id",          group.id())
                        .bind("name",        group.name())
                        .bind("description", group.description())
                        .bind("createdBy",   group.createdBy())
                        .bind("type",        group.type().name())
                        .bind("listingId",   group.listingId())
                        .bind("createdAt",   InstantMapper.toLong(group.createdAt()))
                        .bind("updatedAt",   InstantMapper.toLong(group.updatedAt()))
                        .bind("deletedAt",   InstantMapper.toLong(group.deletedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE chat_groups SET deleted_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}