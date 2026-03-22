package tech.nabor.app.db.repository.sync;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.sync.ResolvedConflict;
import tech.nabor.api.repository.sync.ResolvedConflictRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppResolvedConflictRepository implements ResolvedConflictRepository {

    private final Jdbi jdbi;

    public AppResolvedConflictRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class ResolvedConflictMapper implements RowMapper<ResolvedConflict> {
        @Override
        public ResolvedConflict map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ResolvedConflict(
                    rs.getInt("id"),
                    rs.getString("table_name"),
                    rs.getString("row_id"),
                    rs.getString("field_name"),
                    rs.getString("chosen_value"),
                    InstantMapper.fromNullableLong(rs, "resolved_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<ResolvedConflict> findPrevious(String tableName, String rowId, String fieldName) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM resolved_conflicts
                WHERE table_name = :tableName AND row_id = :rowId AND field_name = :fieldName
                ORDER BY resolved_at DESC
                LIMIT 1
                """)
                        .bind("tableName", tableName)
                        .bind("rowId",     rowId)
                        .bind("fieldName", fieldName)
                        .map(new ResolvedConflictMapper())
                        .findOne()
        );
    }

    @Override
    public List<ResolvedConflict> findByRow(String tableName, String rowId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM resolved_conflicts
                WHERE table_name = :tableName AND row_id = :rowId
                ORDER BY resolved_at DESC
                """)
                        .bind("tableName", tableName)
                        .bind("rowId",     rowId)
                        .map(new ResolvedConflictMapper())
                        .list()
        );
    }

    @Override
    public void save(ResolvedConflict conflict) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO resolved_conflicts
                    (table_name, row_id, field_name, chosen_value, resolved_at)
                VALUES
                    (:tableName, :rowId, :fieldName, :chosenValue, :resolvedAt)
                """)
                        .bind("tableName",   conflict.tableName())
                        .bind("rowId",       conflict.rowId())
                        .bind("fieldName",   conflict.fieldName())
                        .bind("chosenValue", conflict.chosenValue())
                        .bind("resolvedAt",  InstantMapper.toLong(conflict.resolvedAt()))
                        .execute()
        );
    }

    @Override
    public void deleteByRow(String tableName, String rowId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                DELETE FROM resolved_conflicts
                WHERE table_name = :tableName AND row_id = :rowId
                """)
                        .bind("tableName", tableName)
                        .bind("rowId",     rowId)
                        .execute()
        );
    }
}