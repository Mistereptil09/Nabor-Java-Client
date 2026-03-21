package tech.nabor.api.model.db_model.messages;

import tech.nabor.api.model.db_model.enums.GroupRole;

import java.time.Instant;

public record UserInGroup(
        String userId,
        String groupId,
        GroupRole roleInGroup,
        Instant joinedAt,
        Instant leftAt,
        Instant kickedAt,
        boolean isMuted,
        Instant mutedUntil
) {}