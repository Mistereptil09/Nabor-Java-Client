package tech.nabor.api.model.db_model.messages;

import tech.nabor.api.model.db_model.enums.ChatGroupType;

import java.time.Instant;

public record ChatGroup(
        String id,
        String name,
        String description,
        String createdBy,
        ChatGroupType type,
        String listingId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {}
