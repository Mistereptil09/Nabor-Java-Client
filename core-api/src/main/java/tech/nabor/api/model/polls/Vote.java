package tech.nabor.api.model.polls;


import java.time.Instant;

public record Vote(
        String userId,
        String optionId,
        int weight,
        Instant votedAt,
        Instant updatedAt
) {}