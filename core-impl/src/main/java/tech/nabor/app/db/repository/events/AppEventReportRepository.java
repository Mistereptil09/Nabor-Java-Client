package tech.nabor.app.db.repository.events;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.events.EventReport;
import tech.nabor.api.repository.events.EventReportRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppEventReportRepository implements EventReportRepository {

    private final Jdbi jdbi;

    public AppEventReportRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class EventReportMapper implements RowMapper<EventReport> {
        @Override
        public EventReport map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new EventReport(
                    rs.getString("id"),
                    rs.getString("event_id"),
                    rs.getString("reporter_id"),
                    rs.getString("reason"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "resolved_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<EventReport> findByEventId(String eventId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM event_reports WHERE event_id = :eventId")
                        .bind("eventId", eventId)
                        .map(new EventReportMapper())
                        .list()
        );
    }

    @Override
    public List<EventReport> findUnresolved(int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM event_reports
                WHERE resolved_at IS NULL
                ORDER BY created_at ASC
                LIMIT :limit
                """)
                        .bind("limit", limit)
                        .map(new EventReportMapper())
                        .list()
        );
    }

    @Override
    public void save(EventReport report) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO event_reports (id, event_id, reporter_id, reason, created_at, resolved_at)
                VALUES (:id, :eventId, :reporterId, :reason, :createdAt, :resolvedAt)
                ON CONFLICT(id) DO UPDATE SET
                    reason      = excluded.reason,
                    resolved_at = excluded.resolved_at
                """)
                        .bind("id",          report.id())
                        .bind("eventId",     report.eventId())
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
                h.createUpdate("UPDATE event_reports SET resolved_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}