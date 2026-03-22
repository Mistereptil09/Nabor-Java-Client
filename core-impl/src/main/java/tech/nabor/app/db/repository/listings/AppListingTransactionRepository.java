package tech.nabor.app.db.repository.listings;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.listings.ListingTransaction;
import tech.nabor.api.model.enums.TransactionStatus;
import tech.nabor.api.repository.listings.ListingTransactionRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppListingTransactionRepository implements ListingTransactionRepository {

    private final Jdbi jdbi;

    public AppListingTransactionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class ListingTransactionMapper implements RowMapper<ListingTransaction> {
        @Override
        public ListingTransaction map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ListingTransaction(
                    rs.getString("id"),
                    rs.getString("listing_id"),
                    rs.getString("provider_id"),
                    rs.getString("requester_id"),
                    rs.getInt("amount_cents"),
                    rs.getInt("commission_cents"),
                    rs.getString("stripe_session_id"),
                    rs.getString("stripe_payment_intent"),
                    rs.getString("contract_mongo_id"),
                    rs.getString("receipt_mongo_id"),
                    rs.getString("payment_failed_reason"),
                    TransactionStatus.valueOf(rs.getString("status")),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "paid_at"),
                    InstantMapper.fromNullableLong(rs, "completed_at"),
                    InstantMapper.fromNullableLong(rs, "cancelled_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<ListingTransaction> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_transactions WHERE id = :id")
                        .bind("id", id)
                        .map(new ListingTransactionMapper())
                        .findOne()
        );
    }

    @Override
    public List<ListingTransaction> findByListingId(String listingId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_transactions WHERE listing_id = :listingId")
                        .bind("listingId", listingId)
                        .map(new ListingTransactionMapper())
                        .list()
        );
    }

    @Override
    public List<ListingTransaction> findByProviderId(String providerId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_transactions WHERE provider_id = :providerId")
                        .bind("providerId", providerId)
                        .map(new ListingTransactionMapper())
                        .list()
        );
    }

    @Override
    public List<ListingTransaction> findByRequesterId(String requesterId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_transactions WHERE requester_id = :requesterId")
                        .bind("requesterId", requesterId)
                        .map(new ListingTransactionMapper())
                        .list()
        );
    }

    @Override
    public List<ListingTransaction> findByStatus(TransactionStatus status, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM listing_transactions
                WHERE status = :status
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("status", status.name())
                        .bind("limit",  limit)
                        .map(new ListingTransactionMapper())
                        .list()
        );
    }

    @Override
    public void save(ListingTransaction transaction) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO listing_transactions (
                    id, listing_id, provider_id, requester_id, amount_cents, commission_cents,
                    stripe_session_id, stripe_payment_intent, contract_mongo_id, receipt_mongo_id,
                    payment_failed_reason, status, created_at, paid_at, completed_at, cancelled_at
                ) VALUES (
                    :id, :listingId, :providerId, :requesterId, :amountCents, :commissionCents,
                    :stripeSessionId, :stripePaymentIntent, :contractMongoId, :receiptMongoId,
                    :paymentFailedReason, :status, :createdAt, :paidAt, :completedAt, :cancelledAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    amount_cents          = excluded.amount_cents,
                    commission_cents      = excluded.commission_cents,
                    stripe_session_id     = excluded.stripe_session_id,
                    stripe_payment_intent = excluded.stripe_payment_intent,
                    contract_mongo_id     = excluded.contract_mongo_id,
                    receipt_mongo_id      = excluded.receipt_mongo_id,
                    payment_failed_reason = excluded.payment_failed_reason,
                    status                = excluded.status,
                    paid_at               = excluded.paid_at,
                    completed_at          = excluded.completed_at,
                    cancelled_at          = excluded.cancelled_at
                """)
                        .bind("id",                   transaction.id())
                        .bind("listingId",            transaction.listingId())
                        .bind("providerId",           transaction.providerId())
                        .bind("requesterId",          transaction.requesterId())
                        .bind("amountCents",          transaction.amountCents())
                        .bind("commissionCents",      transaction.commissionCents())
                        .bind("stripeSessionId",      transaction.stripeSessionId())
                        .bind("stripePaymentIntent",  transaction.stripePaymentIntent())
                        .bind("contractMongoId",      transaction.contractMongoId())
                        .bind("receiptMongoId",       transaction.receiptMongoId())
                        .bind("paymentFailedReason",  transaction.paymentFailedReason())
                        .bind("status",               transaction.status().name())
                        .bind("createdAt",            InstantMapper.toLong(transaction.createdAt()))
                        .bind("paidAt",               InstantMapper.toLong(transaction.paidAt()))
                        .bind("completedAt",          InstantMapper.toLong(transaction.completedAt()))
                        .bind("cancelledAt",          InstantMapper.toLong(transaction.cancelledAt()))
                        .execute()
        );
    }
}