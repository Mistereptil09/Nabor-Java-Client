package tech.nabor.api.model.messages;

import tech.nabor.api.model.enums.GroupRole;

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