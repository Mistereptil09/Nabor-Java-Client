package tech.nabor.api.model.db_model.messages;


import java.time.Instant;

public record MessageMetadata(
        String id,
        String mongoMessageId,
        String groupId,
        String senderId,
        Instant sentAt,
        Instant editedAt,
        boolean isDeleted,
        Instant deletedAt,
        String parentMessageId
) {}