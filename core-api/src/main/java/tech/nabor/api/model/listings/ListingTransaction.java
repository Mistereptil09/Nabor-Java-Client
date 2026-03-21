package tech.nabor.api.model.listings;

import tech.nabor.api.model.enums.TransactionStatus;

import java.time.Instant;

public record ListingTransaction(
        String id,
        String listingId,
        String providerId,
        String requesterId,
        int amountCents,
        int commissionCents,
        String stripeSessionId,
        String stripePaymentIntent,
        String contractMongoId,
        String receiptMongoId,
        String paymentFailedReason,
        TransactionStatus status,
        Instant createdAt,
        Instant paidAt,
        Instant completedAt,
        Instant cancelledAt
) {}