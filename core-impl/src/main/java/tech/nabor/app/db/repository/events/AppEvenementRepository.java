package tech.nabor.app.db.repository.events;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.events.Evenement;
import tech.nabor.api.model.enums.EventStatus;
import tech.nabor.api.repository.events.EvenementRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppEvenementRepository implements EvenementRepository {

    private final Jdbi jdbi;

    public AppEvenementRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class EvenementMapper implements RowMapper<Evenement> {
        @Override
        public Evenement map(ResultSet rs, StatementContext ctx) throws SQLException {
            int categoryId = rs.getInt("category_id");
            Integer categoryIdNullable = rs.wasNull() ? null : categoryId;
            return new Evenement(
                    rs.getString("id"),
                    rs.getString("creator_id"),
                    rs.getString("neighbourhood_id"),
                    categoryIdNullable,
                    rs.getString("group_id"),
                    rs.getString("title"),
                    EventStatus.valueOf(rs.getString("status")),
                    rs.getString("invite_code"),
                    rs.getInt("cost_cents"),
                    InstantMapper.fromNullableLong(rs, "starts_at"),
                    InstantMapper.fromNullableLong(rs, "ends_at"),
                    rs.getObject("max_participants") != null ? rs.getInt("max_participants") : null,
                    rs.getInt("refund_deadline_hours"),
                    rs.getString("mongo_document_id"),
                    InstantMapper.fromNullableLong(rs, "published_at"),
                    InstantMapper.fromNullableLong(rs, "cancelled_at"),
                    InstantMapper.fromNullableLong(rs, "completed_at"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at"),
                    InstantMapper.fromNullableLong(rs, "deleted_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<Evenement> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM evenements WHERE deleted_at IS NULL ORDER BY created_at DESC")
                        .map(new EvenementMapper())
                        .list()
        );
    }

    @Override
    public Optional<Evenement> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM evenements WHERE id = :id")
                        .bind("id", id)
                        .map(new EvenementMapper())
                        .findOne()
        );
    }

    @Override
    public List<Evenement> findByCreatorId(String creatorId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM evenements
                WHERE creator_id = :creatorId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """)
                        .bind("creatorId", creatorId)
                        .map(new EvenementMapper())
                        .list()
        );
    }

    @Override
    public List<Evenement> findByNeighbourhood(String neighbourhoodId, EventStatus status, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM evenements
                WHERE neighbourhood_id = :neighbourhoodId
                AND status = :status
                AND deleted_at IS NULL
                ORDER BY starts_at ASC
                LIMIT :limit
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .bind("status",          status.name())
                        .bind("limit",           limit)
                        .map(new EvenementMapper())
                        .list()
        );
    }

    @Override
    public List<Evenement> findUpcoming(String neighbourhoodId, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM evenements
                WHERE neighbourhood_id = :neighbourhoodId
                AND status = 'open'
                AND starts_at > :now
                AND deleted_at IS NULL
                ORDER BY starts_at ASC
                LIMIT :limit
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .bind("now",             System.currentTimeMillis())
                        .bind("limit",           limit)
                        .map(new EvenementMapper())
                        .list()
        );
    }

    @Override
    public List<Evenement> findByStatus(EventStatus status, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM evenements
                WHERE status = :status AND deleted_at IS NULL
                ORDER BY starts_at ASC
                LIMIT :limit
                """)
                        .bind("status", status.name())
                        .bind("limit",  limit)
                        .map(new EvenementMapper())
                        .list()
        );
    }

    @Override
    public void save(Evenement evenement) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO evenements (
                    id, creator_id, neighbourhood_id, category_id, group_id, title,
                    status, invite_code, cost_cents, starts_at, ends_at, max_participants,
                    refund_deadline_hours, mongo_document_id, published_at, cancelled_at,
                    completed_at, created_at, updated_at, deleted_at
                ) VALUES (
                    :id, :creatorId, :neighbourhoodId, :categoryId, :groupId, :title,
                    :status, :inviteCode, :costCents, :startsAt, :endsAt, :maxParticipants,
                    :refundDeadlineHours, :mongoDocumentId, :publishedAt, :cancelledAt,
                    :completedAt, :createdAt, :updatedAt, :deletedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    neighbourhood_id      = excluded.neighbourhood_id,
                    category_id           = excluded.category_id,
                    group_id              = excluded.group_id,
                    title                 = excluded.title,
                    status                = excluded.status,
                    invite_code           = excluded.invite_code,
                    cost_cents            = excluded.cost_cents,
                    starts_at             = excluded.starts_at,
                    ends_at               = excluded.ends_at,
                    max_participants      = excluded.max_participants,
                    refund_deadline_hours = excluded.refund_deadline_hours,
                    mongo_document_id     = excluded.mongo_document_id,
                    published_at          = excluded.published_at,
                    cancelled_at          = excluded.cancelled_at,
                    completed_at          = excluded.completed_at,
                    updated_at            = excluded.updated_at,
                    deleted_at            = excluded.deleted_at
                """)
                        .bind("id",                   evenement.id())
                        .bind("creatorId",            evenement.creatorId())
                        .bind("neighbourhoodId",      evenement.neighbourhoodId())
                        .bind("categoryId",           evenement.categoryId())
                        .bind("groupId",              evenement.groupId())
                        .bind("title",                evenement.title())
                        .bind("status",               evenement.status().name())
                        .bind("inviteCode",           evenement.inviteCode())
                        .bind("costCents",            evenement.costCents())
                        .bind("startsAt",             InstantMapper.toLong(evenement.startsAt()))
                        .bind("endsAt",               InstantMapper.toLong(evenement.endsAt()))
                        .bind("maxParticipants",      evenement.maxParticipants())
                        .bind("refundDeadlineHours",  evenement.refundDeadlineHours())
                        .bind("mongoDocumentId",      evenement.mongoDocumentId())
                        .bind("publishedAt",          InstantMapper.toLong(evenement.publishedAt()))
                        .bind("cancelledAt",          InstantMapper.toLong(evenement.cancelledAt()))
                        .bind("completedAt",          InstantMapper.toLong(evenement.completedAt()))
                        .bind("createdAt",            InstantMapper.toLong(evenement.createdAt()))
                        .bind("updatedAt",            InstantMapper.toLong(evenement.updatedAt()))
                        .bind("deletedAt",            InstantMapper.toLong(evenement.deletedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE evenements SET deleted_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}