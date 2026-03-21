package tech.nabor.api.model.db_model.social;

import java.time.Instant;

public record Friendship(
        String id,
        String user1Id,
        String user2Id,
        Instant friendedAt,
        Instant unfriendedAt,
        String groupId
) {}
