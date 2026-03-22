package tech.nabor.app.db.repository.messages;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.messages.MessageMetadata;
import tech.nabor.api.repository.messages.MessageMetadataRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class AppMessageMetadataRepository implements MessageMetadataRepository {

    private final Jdbi jdbi;

    public AppMessageMetadataRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class MessageMetadataMapper implements RowMapper<MessageMetadata> {
        @Override
        public MessageMetadata map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new MessageMetadata(
                    rs.getString("id"),
                    rs.getString("mongo_message_id"),
                    rs.getString("group_id"),
                    rs.getString("sender_id"),
                    InstantMapper.fromNullableLong(rs, "sent_at"),
                    InstantMapper.fromNullableLong(rs, "edited_at"),
                    rs.getInt("is_deleted") == 1,
                    InstantMapper.fromNullableLong(rs, "deleted_at"),
                    rs.getString("parent_message_id")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<MessageMetadata> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM message_metadata WHERE id = :id")
                        .bind("id", id)
                        .map(new MessageMetadataMapper())
                        .findOne()
        );
    }

    @Override
    public List<MessageMetadata> findByGroupId(String groupId, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM message_metadata
                WHERE group_id = :groupId AND is_deleted = 0
                ORDER BY sent_at DESC
                LIMIT :limit
                """)
                        .bind("groupId", groupId)
                        .bind("limit",   limit)
                        .map(new MessageMetadataMapper())
                        .list()
        );
    }

    @Override
    public List<MessageMetadata> findByGroupIdBefore(String groupId, Instant before, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM message_metadata
                WHERE group_id = :groupId AND is_deleted = 0 AND sent_at < :before
                ORDER BY sent_at DESC
                LIMIT :limit
                """)
                        .bind("groupId", groupId)
                        .bind("before",  InstantMapper.toLong(before))
                        .bind("limit",   limit)
                        .map(new MessageMetadataMapper())
                        .list()
        );
    }

    @Override
    public List<MessageMetadata> findThreadByParentId(String parentMessageId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM message_metadata
                WHERE parent_message_id = :parentMessageId AND is_deleted = 0
                ORDER BY sent_at ASC
                """)
                        .bind("parentMessageId", parentMessageId)
                        .map(new MessageMetadataMapper())
                        .list()
        );
    }

    @Override
    public void save(MessageMetadata message) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO message_metadata
                    (id, mongo_message_id, group_id, sender_id, sent_at, edited_at,
                     is_deleted, deleted_at, parent_message_id)
                VALUES
                    (:id, :mongoMessageId, :groupId, :senderId, :sentAt, :editedAt,
                     :isDeleted, :deletedAt, :parentMessageId)
                ON CONFLICT(id) DO UPDATE SET
                    mongo_message_id  = excluded.mongo_message_id,
                    edited_at         = excluded.edited_at,
                    is_deleted        = excluded.is_deleted,
                    deleted_at        = excluded.deleted_at
                """)
                        .bind("id",              message.id())
                        .bind("mongoMessageId",  message.mongoMessageId())
                        .bind("groupId",         message.groupId())
                        .bind("senderId",        message.senderId())
                        .bind("sentAt",          InstantMapper.toLong(message.sentAt()))
                        .bind("editedAt",        InstantMapper.toLong(message.editedAt()))
                        .bind("isDeleted",       message.isDeleted() ? 1 : 0)
                        .bind("deletedAt",       InstantMapper.toLong(message.deletedAt()))
                        .bind("parentMessageId", message.parentMessageId())
                        .execute()
        );
    }

    @Override
    public void markDeleted(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE message_metadata
                SET is_deleted = 1, deleted_at = :now
                WHERE id = :id
                """)
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}