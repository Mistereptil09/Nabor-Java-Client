package tech.nabor.app.db.repository.polls;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.polls.Vote;
import tech.nabor.api.repository.polls.VoteRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppVoteRepository implements VoteRepository {

    private final Jdbi jdbi;

    public AppVoteRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class VoteMapper implements RowMapper<Vote> {
        @Override
        public Vote map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Vote(
                    rs.getString("user_id"),
                    rs.getString("option_id"),
                    rs.getInt("weight"),
                    InstantMapper.fromNullableLong(rs, "voted_at"),
                    InstantMapper.fromNullableLong(rs, "updated_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<Vote> findByOptionId(String optionId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM votes WHERE option_id = :optionId")
                        .bind("optionId", optionId)
                        .map(new VoteMapper())
                        .list()
        );
    }

    @Override
    public List<Vote> findByUserAndPoll(String userId, String pollId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT v.* FROM votes v
                JOIN poll_options po ON v.option_id = po.id
                WHERE v.user_id = :userId AND po.poll_id = :pollId
                """)
                        .bind("userId", userId)
                        .bind("pollId", pollId)
                        .map(new VoteMapper())
                        .list()
        );
    }

    @Override
    public Optional<Vote> findByUserAndOption(String userId, String optionId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM votes
                WHERE user_id = :userId AND option_id = :optionId
                """)
                        .bind("userId",   userId)
                        .bind("optionId", optionId)
                        .map(new VoteMapper())
                        .findOne()
        );
    }

    @Override
    public int countByOptionId(String optionId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT COUNT(*) FROM votes WHERE option_id = :optionId")
                        .bind("optionId", optionId)
                        .mapTo(Integer.class)
                        .one()
        );
    }

    @Override
    public void save(Vote vote) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO votes (user_id, option_id, weight, voted_at, updated_at)
                VALUES (:userId, :optionId, :weight, :votedAt, :updatedAt)
                ON CONFLICT(user_id, option_id) DO UPDATE SET
                    weight     = excluded.weight,
                    updated_at = :updatedAt
                """)
                        .bind("userId",    vote.userId())
                        .bind("optionId",  vote.optionId())
                        .bind("weight",    vote.weight())
                        .bind("votedAt",   InstantMapper.toLong(vote.votedAt()))
                        .bind("updatedAt", InstantMapper.toLong(vote.updatedAt()))
                        .execute()
        );
    }

    @Override
    public void deleteByUserAndPoll(String userId, String pollId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                DELETE FROM votes
                WHERE user_id = :userId
                AND option_id IN (
                    SELECT id FROM poll_options WHERE poll_id = :pollId
                )
                """)
                        .bind("userId", userId)
                        .bind("pollId", pollId)
                        .execute()
        );
    }
}