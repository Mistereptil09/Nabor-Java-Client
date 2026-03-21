package tech.nabor.api.model.db_model.messages;

import java.time.Instant;

public record MessageReadReceipt(
        String messageId,
        String userId,
        Instant readAt
) {}