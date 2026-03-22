package tech.nabor.app.db.repository.listings;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.listings.ListingReport;
import tech.nabor.api.repository.listings.ListingReportRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppListingReportRepository implements ListingReportRepository {

    private final Jdbi jdbi;

    public AppListingReportRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class ListingReportMapper implements RowMapper<ListingReport> {
        @Override
        public ListingReport map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ListingReport(
                    rs.getString("id"),
                    rs.getString("listing_id"),
                    rs.getString("reporter_id"),
                    rs.getString("reason"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "resolved_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<ListingReport> findByListingId(String listingId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM listing_reports WHERE listing_id = :listingId")
                        .bind("listingId", listingId)
                        .map(new ListingReportMapper())
                        .list()
        );
    }

    @Override
    public List<ListingReport> findUnresolved(int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM listing_reports
                WHERE resolved_at IS NULL
                ORDER BY created_at ASC
                LIMIT :limit
                """)
                        .bind("limit", limit)
                        .map(new ListingReportMapper())
                        .list()
        );
    }

    @Override
    public void save(ListingReport report) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO listing_reports (id, listing_id, reporter_id, reason, created_at, resolved_at)
                VALUES (:id, :listingId, :reporterId, :reason, :createdAt, :resolvedAt)
                ON CONFLICT(id) DO UPDATE SET
                    reason      = excluded.reason,
                    resolved_at = excluded.resolved_at
                """)
                        .bind("id",          report.id())
                        .bind("listingId",   report.listingId())
                        .bind("reporterId",  report.reporterId())
                        .bind("reason",      report.reason())
                        .bind("createdAt",   InstantMapper.toLong(report.createdAt()))
                        .bind("resolvedAt",  InstantMapper.toLong(report.resolvedAt()))
                        .execute()
        );
    }

    @Override
    public void resolve(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE listing_reports SET resolved_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}