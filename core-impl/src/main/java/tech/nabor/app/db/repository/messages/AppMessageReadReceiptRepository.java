package tech.nabor.app.db.repository.messages;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.messages.MessageReadReceipt;
import tech.nabor.api.repository.messages.MessageReadReceiptRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppMessageReadReceiptRepository implements MessageReadReceiptRepository {

    private final Jdbi jdbi;

    public AppMessageReadReceiptRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class MessageReadReceiptMapper implements RowMapper<MessageReadReceipt> {
        @Override
        public MessageReadReceipt map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new MessageReadReceipt(
                    rs.getString("message_id"),
                    rs.getString("user_id"),
                    InstantMapper.fromNullableLong(rs, "read_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<MessageReadReceipt> findByMessageId(String messageId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM message_read_receipts WHERE message_id = :messageId")
                        .bind("messageId", messageId)
                        .map(new MessageReadReceiptMapper())
                        .list()
        );
    }

    @Override
    public boolean hasRead(String userId, String messageId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT COUNT(*) FROM message_read_receipts
                WHERE user_id = :userId AND message_id = :messageId
                """)
                        .bind("userId",    userId)
                        .bind("messageId", messageId)
                        .mapTo(Integer.class)
                        .one() > 0
        );
    }

    @Override
    public void save(MessageReadReceipt receipt) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO message_read_receipts (message_id, user_id, read_at)
                VALUES (:messageId, :userId, :readAt)
                ON CONFLICT(message_id, user_id) DO UPDATE SET
                    read_at = excluded.read_at
                """)
                        .bind("messageId", receipt.messageId())
                        .bind("userId",    receipt.userId())
                        .bind("readAt",    InstantMapper.toLong(receipt.readAt()))
                        .execute()
        );
    }
}