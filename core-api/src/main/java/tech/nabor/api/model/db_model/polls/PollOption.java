package tech.nabor.api.model.db_model.polls;

import java.time.Instant;

public record PollOption(
        String id,
        String pollId,
        String label,
        Instant createdAt
) {}