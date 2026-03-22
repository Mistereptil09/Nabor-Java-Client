package tech.nabor.app.db.repository.polls;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.polls.PollOption;
import tech.nabor.api.repository.polls.PollOptionRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppPollOptionRepository implements PollOptionRepository {

    private final Jdbi jdbi;

    public AppPollOptionRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class PollOptionMapper implements RowMapper<PollOption> {
        @Override
        public PollOption map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PollOption(
                    rs.getString("id"),
                    rs.getString("poll_id"),
                    rs.getString("label"),
                    InstantMapper.fromNullableLong(rs, "created_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<PollOption> findByPollId(String pollId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM poll_options WHERE poll_id = :pollId")
                        .bind("pollId", pollId)
                        .map(new PollOptionMapper())
                        .list()
        );
    }

    @Override
    public Optional<PollOption> findById(String id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM poll_options WHERE id = :id")
                        .bind("id", id)
                        .map(new PollOptionMapper())
                        .findOne()
        );
    }

    @Override
    public void save(PollOption option) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO poll_options (id, poll_id, label, created_at)
                VALUES (:id, :pollId, :label, :createdAt)
                ON CONFLICT(id) DO UPDATE SET
                    label      = excluded.label
                """)
                        .bind("id",        option.id())
                        .bind("pollId",    option.pollId())
                        .bind("label",     option.label())
                        .bind("createdAt", InstantMapper.toLong(option.createdAt()))
                        .execute()
        );
    }

    @Override
    public void deleteByPollId(String pollId) {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM poll_options WHERE poll_id = :pollId")
                        .bind("pollId", pollId)
                        .execute()
        );
    }
}