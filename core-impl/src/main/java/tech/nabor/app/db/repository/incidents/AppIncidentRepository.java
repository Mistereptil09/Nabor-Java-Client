package tech.nabor.app.db.repository.incidents;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.incidents.Incident;
import tech.nabor.api.model.enums.IncidentSeverity;
import tech.nabor.api.model.enums.IncidentStatus;
import tech.nabor.api.repository.incidents.IncidentRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppIncidentRepository implements IncidentRepository {

    private final Jdbi jdbi;

    public AppIncidentRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class IncidentMapper implements RowMapper<Incident> {
        @Override
        public Incident map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Incident(
                    rs.getString("id"),
                    rs.getString("reporter_id"),
                    rs.getString("assigned_to"),
                    rs.getString("neighbourhood_id"),
                    rs.getString("mongo_document_id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    IncidentSeverity.valueOf(rs.getString("severity")),
                    IncidentStatus.valueOf(rs.getString("status")),
                    InstantMapper.fromNullableLong(rs, "assigned_at"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at"),
                    InstantMapper.fromNullableLong(rs, "resolved_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<Incident> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM incidents ORDER BY created_at DESC")
                        .map(new IncidentMapper())
                        .list()
        );
    }

    @Override
    public Optional<Incident> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM incidents WHERE id = :id")
                        .bind("id", id)
                        .map(new IncidentMapper())
                        .findOne()
        );
    }

    @Override
    public List<Incident> findByReporterId(String reporterId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM incidents
                WHERE reporter_id = :reporterId
                ORDER BY created_at DESC
                """)
                        .bind("reporterId", reporterId)
                        .map(new IncidentMapper())
                        .list()
        );
    }

    @Override
    public List<Incident> findByAssignedTo(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM incidents
                WHERE assigned_to = :userId
                ORDER BY created_at DESC
                """)
                        .bind("userId", userId)
                        .map(new IncidentMapper())
                        .list()
        );
    }

    @Override
    public List<Incident> findByNeighbourhood(String neighbourhoodId, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM incidents
                WHERE neighbourhood_id = :neighbourhoodId
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .bind("limit",           limit)
                        .map(new IncidentMapper())
                        .list()
        );
    }

    @Override
    public List<Incident> findByStatus(IncidentStatus status, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM incidents
                WHERE status = :status
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("status", status.name())
                        .bind("limit",  limit)
                        .map(new IncidentMapper())
                        .list()
        );
    }

    @Override
    public List<Incident> findBySeverity(IncidentSeverity severity, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM incidents
                WHERE severity = :severity
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("severity", severity.name())
                        .bind("limit",    limit)
                        .map(new IncidentMapper())
                        .list()
        );
    }

    @Override
    public List<Incident> findOpen(String neighbourhoodId, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM incidents
                WHERE neighbourhood_id = :neighbourhoodId
                AND status IN ('open', 'in_progress')
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .bind("limit",           limit)
                        .map(new IncidentMapper())
                        .list()
        );
    }

    @Override
    public void save(Incident incident) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO incidents (
                    id, reporter_id, assigned_to, neighbourhood_id, mongo_document_id,
                    title, description, severity, status, assigned_at,
                    created_at, updated_at, resolved_at
                ) VALUES (
                    :id, :reporterId, :assignedTo, :neighbourhoodId, :mongoDocumentId,
                    :title, :description, :severity, :status, :assignedAt,
                    :createdAt, :updatedAt, :resolvedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    assigned_to       = excluded.assigned_to,
                    neighbourhood_id  = excluded.neighbourhood_id,
                    mongo_document_id = excluded.mongo_document_id,
                    title             = excluded.title,
                    description       = excluded.description,
                    severity          = excluded.severity,
                    status            = excluded.status,
                    assigned_at       = excluded.assigned_at,
                    updated_at        = excluded.updated_at,
                    resolved_at       = excluded.resolved_at
                """)
                        .bind("id",               incident.id())
                        .bind("reporterId",       incident.reporterId())
                        .bind("assignedTo",       incident.assignedTo())
                        .bind("neighbourhoodId",  incident.neighbourhoodId())
                        .bind("mongoDocumentId",  incident.mongoDocumentId())
                        .bind("title",            incident.title())
                        .bind("description",      incident.description())
                        .bind("severity",         incident.severity().name())
                        .bind("status",           incident.status().name())
                        .bind("assignedAt",       InstantMapper.toLong(incident.assignedAt()))
                        .bind("createdAt",        InstantMapper.toLong(incident.createdAt()))
                        .bind("updatedAt",        InstantMapper.toLong(incident.updatedAt()))
                        .bind("resolvedAt",       InstantMapper.toLong(incident.resolvedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM incidents WHERE id = :id")
                        .bind("id", id).execute());
    }

    @Override
    public void assign(String id, String userId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE incidents
                SET assigned_to = :userId, assigned_at = :now, status = 'in_progress'
                WHERE id = :id
                """)
                        .bind("userId", userId)
                        .bind("now",    System.currentTimeMillis())
                        .bind("id",     id)
                        .execute()
        );
    }

    @Override
    public void resolve(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE incidents
                SET status = 'resolved', resolved_at = :now
                WHERE id = :id
                """)
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}