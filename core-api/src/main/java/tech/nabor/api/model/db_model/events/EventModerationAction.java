package tech.nabor.api.model.db_model.events;

import tech.nabor.api.model.db_model.enums.ModerationAction;

import java.time.Instant;

public record EventModerationAction(
        String id,
        String eventId,
        String moderatorId,
        ModerationAction action,
        String reason,
        Instant createdAt
) {}