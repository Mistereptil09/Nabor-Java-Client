package tech.nabor.app.db.repository.polls;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.polls.Poll;
import tech.nabor.api.model.enums.PollType;
import tech.nabor.api.repository.polls.PollRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppPollRepository implements PollRepository {

    private final Jdbi jdbi;

    public AppPollRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class PollMapper implements RowMapper<Poll> {
        @Override
        public Poll map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Poll(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("creator_id"),
                    rs.getString("neighbourhood_id"),
                    PollType.valueOf(rs.getString("poll_type")),
                    InstantMapper.fromNullableLong(rs, "starts_at"),
                    InstantMapper.fromNullableLong(rs, "ends_at"),
                    rs.getInt("is_anonymous") == 1,
                    InstantMapper.fromNullableLong(rs, "closed_at"),
                    rs.getString("closed_by"),
                    InstantMapper.fromNullableLong(rs, "created_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at"),
                    InstantMapper.fromNullableLong(rs, "deleted_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<Poll> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM polls WHERE id = :id")
                        .bind("id", id)
                        .map(new PollMapper())
                        .findOne()
        );
    }

    @Override
    public List<Poll> findByCreatorId(String creatorId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM polls
                WHERE creator_id = :creatorId AND deleted_at IS NULL
                ORDER BY created_at DESC
                """)
                        .bind("creatorId", creatorId)
                        .map(new PollMapper())
                        .list()
        );
    }

    @Override
    public List<Poll> findByNeighbourhood(String neighbourhoodId, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM polls
                WHERE neighbourhood_id = :neighbourhoodId AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .bind("limit",           limit)
                        .map(new PollMapper())
                        .list()
        );
    }

    @Override
    public List<Poll> findActive(String neighbourhoodId, int limit) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM polls
                WHERE neighbourhood_id = :neighbourhoodId
                AND deleted_at IS NULL
                AND closed_at IS NULL
                AND (ends_at IS NULL OR ends_at > :now)
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                        .bind("neighbourhoodId", neighbourhoodId)
                        .bind("now",             System.currentTimeMillis())
                        .bind("limit",           limit)
                        .map(new PollMapper())
                        .list()
        );
    }

    @Override
    public void save(Poll poll) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO polls (
                    id, title, description, creator_id, neighbourhood_id, poll_type,
                    starts_at, ends_at, is_anonymous, closed_at, closed_by,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    :id, :title, :description, :creatorId, :neighbourhoodId, :pollType,
                    :startsAt, :endsAt, :isAnonymous, :closedAt, :closedBy,
                    :createdAt, :updatedAt, :deletedAt
                )
                ON CONFLICT(id) DO UPDATE SET
                    title            = excluded.title,
                    description      = excluded.description,
                    neighbourhood_id = excluded.neighbourhood_id,
                    poll_type        = excluded.poll_type,
                    starts_at        = excluded.starts_at,
                    ends_at          = excluded.ends_at,
                    is_anonymous     = excluded.is_anonymous,
                    closed_at        = excluded.closed_at,
                    closed_by        = excluded.closed_by,
                    updated_at       = excluded.updated_at,
                    deleted_at       = excluded.deleted_at
                """)
                        .bind("id",              poll.id())
                        .bind("title",           poll.title())
                        .bind("description",     poll.description())
                        .bind("creatorId",       poll.creatorId())
                        .bind("neighbourhoodId", poll.neighbourhoodId())
                        .bind("pollType",        poll.pollType().name())
                        .bind("startsAt",        InstantMapper.toLong(poll.startsAt()))
                        .bind("endsAt",          InstantMapper.toLong(poll.endsAt()))
                        .bind("isAnonymous",     poll.isAnonymous() ? 1 : 0)
                        .bind("closedAt",        InstantMapper.toLong(poll.closedAt()))
                        .bind("closedBy",        poll.closedBy())
                        .bind("createdAt",       InstantMapper.toLong(poll.createdAt()))
                        .bind("updatedAt",       InstantMapper.toLong(poll.updatedAt()))
                        .bind("deletedAt",       InstantMapper.toLong(poll.deletedAt()))
                        .execute()
        );
    }

    @Override
    public void close(String id, String closedBy) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE polls SET closed_at = :now, closed_by = :closedBy WHERE id = :id
                """)
                        .bind("now",      System.currentTimeMillis())
                        .bind("closedBy", closedBy)
                        .bind("id",       id)
                        .execute()
        );
    }

    @Override
    public void delete(String id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE polls SET deleted_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }
}