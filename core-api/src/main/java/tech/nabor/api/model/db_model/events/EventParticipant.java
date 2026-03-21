package tech.nabor.api.model.db_model.events;

import tech.nabor.api.model.db_model.enums.ParticipantStatus;
import tech.nabor.api.model.db_model.enums.PaymentStatus;

import java.time.Instant;

public record EventParticipant(
        String userId,
        String eventId,
        ParticipantStatus status,
        PaymentStatus paymentStatus,
        String stripeSessionId,
        String stripePaymentIntent,
        int amountCents,
        Instant registeredAt,
        Instant promotedAt,
        Instant paidAt,
        Instant cancelledAt,
        Instant notifiedAt,
        Instant refundedAt,
        String refundStripeId
) {}