package tech.nabor.app.db.repository.events;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.events.EventParticipant;
import tech.nabor.api.model.enums.ParticipantStatus;
import tech.nabor.api.model.enums.PaymentStatus;
import tech.nabor.api.repository.events.EventParticipantRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppEventParticipantRepository implements EventParticipantRepository {

    private final Jdbi jdbi;

    public AppEventParticipantRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class EventParticipantMapper implements RowMapper<EventParticipant> {
        @Override
        public EventParticipant map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new EventParticipant(
                    rs.getString("user_id"),
                    rs.getString("event_id"),
                    ParticipantStatus.valueOf(rs.getString("status")),
                    PaymentStatus.valueOf(rs.getString("payment_status")),
                    rs.getString("stripe_session_id"),
                    rs.getString("stripe_payment_intent"),
                    rs.getInt("amount_cents"),
                    InstantMapper.fromNullableLong(rs, "registered_at"),
                    InstantMapper.fromNullableLong(rs, "promoted_at"),
                    InstantMapper.fromNullableLong(rs, "paid_at"),
                    InstantMapper.fromNullableLong(rs, "cancelled_at"),
                    InstantMapper.fromNullableLong(rs, "notified_at"),
                    InstantMapper.fromNullableLong(rs, "refunded_at"),
                    rs.getString("refund_stripe_id")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<EventParticipant> findByUserAndEvent(String userId, String eventId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM event_participants
                WHERE user_id = :userId AND event_id = :eventId
                """)
                        .bind("userId",  userId)
                        .bind("eventId", eventId)
                        .map(new EventParticipantMapper())
                        .findOne()
        );
    }

    @Override
    public List<EventParticipant> findByEventId(String eventId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM event_participants WHERE event_id = :eventId")
                        .bind("eventId", eventId)
                        .map(new EventParticipantMapper())
                        .list()
        );
    }

    @Override
    public List<EventParticipant> findByEventAndStatus(String eventId, ParticipantStatus status) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM event_participants
                WHERE event_id = :eventId AND status = :status
                ORDER BY registered_at ASC
                """)
                        .bind("eventId", eventId)
                        .bind("status",  status.name())
                        .map(new EventParticipantMapper())
                        .list()
        );
    }

    @Override
    public List<EventParticipant> findByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM event_participants WHERE user_id = :userId")
                        .bind("userId", userId)
                        .map(new EventParticipantMapper())
                        .list()
        );
    }

    @Override
    public int countRegistered(String eventId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT COUNT(*) FROM event_participants
                WHERE event_id = :eventId AND status = 'registered'
                """)
                        .bind("eventId", eventId)
                        .mapTo(Integer.class)
                        .one()
        );
    }

    @Override
    public void save(EventParticipant participant) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO event_participants (
                    user_id, event_id, status, payment_status, stripe_session_id,
                    stripe_payment_intent, amount_cents, registered_at, promoted_at,
                    paid_at, cancelled_at, notified_at, refunded_at, refund_stripe_id
                ) VALUES (
                    :userId, :eventId, :status, :paymentStatus, :stripeSessionId,
                    :stripePaymentIntent, :amountCents, :registeredAt, :promotedAt,
                    :paidAt, :cancelledAt, :notifiedAt, :refundedAt, :refundStripeId
                )
                ON CONFLICT(user_id, event_id) DO UPDATE SET
                    status                = excluded.status,
                    payment_status        = excluded.payment_status,
                    stripe_session_id     = excluded.stripe_session_id,
                    stripe_payment_intent = excluded.stripe_payment_intent,
                    amount_cents          = excluded.amount_cents,
                    promoted_at           = excluded.promoted_at,
                    paid_at               = excluded.paid_at,
                    cancelled_at          = excluded.cancelled_at,
                    notified_at           = excluded.notified_at,
                    refunded_at           = excluded.refunded_at,
                    refund_stripe_id      = excluded.refund_stripe_id
                """)
                        .bind("userId",               participant.userId())
                        .bind("eventId",              participant.eventId())
                        .bind("status",               participant.status().name())
                        .bind("paymentStatus",        participant.paymentStatus().name())
                        .bind("stripeSessionId",      participant.stripeSessionId())
                        .bind("stripePaymentIntent",  participant.stripePaymentIntent())
                        .bind("amountCents",          participant.amountCents())
                        .bind("registeredAt",         InstantMapper.toLong(participant.registeredAt()))
                        .bind("promotedAt",           InstantMapper.toLong(participant.promotedAt()))
                        .bind("paidAt",               InstantMapper.toLong(participant.paidAt()))
                        .bind("cancelledAt",          InstantMapper.toLong(participant.cancelledAt()))
                        .bind("notifiedAt",           InstantMapper.toLong(participant.notifiedAt()))
                        .bind("refundedAt",           InstantMapper.toLong(participant.refundedAt()))
                        .bind("refundStripeId",       participant.refundStripeId())
                        .execute()
        );
    }

    @Override
    public void cancel(String userId, String eventId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE event_participants
                SET status = 'cancelled', cancelled_at = :now
                WHERE user_id = :userId AND event_id = :eventId
                """)
                        .bind("now",     System.currentTimeMillis())
                        .bind("userId",  userId)
                        .bind("eventId", eventId)
                        .execute()
        );
    }
}