package tech.nabor.api.model.user;

import java.time.Instant;

public record UserNotificationPreferences(
        String userId,
        boolean notifNewFollower,
        boolean notifNewListing,
        boolean notifNewEvent,
        boolean notifNewPoll,
        boolean notifWaitlist,
        boolean notifMessage,
        Instant updatedAt
) {}