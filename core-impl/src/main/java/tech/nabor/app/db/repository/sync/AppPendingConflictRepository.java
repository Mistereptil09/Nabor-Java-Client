package tech.nabor.app.db.repository.sync;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.sync.PendingConflict;
import tech.nabor.api.repository.sync.PendingConflictRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppPendingConflictRepository implements PendingConflictRepository {

    private final Jdbi jdbi;

    public AppPendingConflictRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class PendingConflictMapper implements RowMapper<PendingConflict> {
        @Override
        public PendingConflict map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PendingConflict(
                    rs.getInt("id"),
                    rs.getString("table_name"),
                    rs.getString("row_id"),
                    rs.getString("field_name"),
                    rs.getString("local_value"),
                    rs.getString("remote_value"),
                    InstantMapper.fromNullableLong(rs, "detected_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<PendingConflict> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM pending_conflicts ORDER BY detected_at ASC")
                        .map(new PendingConflictMapper())
                        .list()
        );
    }

    @Override
    public List<PendingConflict> findByTable(String tableName) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM pending_conflicts WHERE table_name = :tableName")
                        .bind("tableName", tableName)
                        .map(new PendingConflictMapper())
                        .list()
        );
    }

    @Override
    public List<PendingConflict> findByRow(String tableName, String rowId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM pending_conflicts
                WHERE table_name = :tableName AND row_id = :rowId
                """)
                        .bind("tableName", tableName)
                        .bind("rowId",     rowId)
                        .map(new PendingConflictMapper())
                        .list()
        );
    }

    @Override
    public boolean hasConflicts() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT COUNT(*) FROM pending_conflicts")
                        .mapTo(Integer.class)
                        .one() > 0
        );
    }

    @Override
    public void save(PendingConflict conflict) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO pending_conflicts
                    (table_name, row_id, field_name, local_value, remote_value, detected_at)
                VALUES
                    (:tableName, :rowId, :fieldName, :localValue, :remoteValue, :detectedAt)
                """)
                        .bind("tableName",  conflict.tableName())
                        .bind("rowId",      conflict.rowId())
                        .bind("fieldName",  conflict.fieldName())
                        .bind("localValue", conflict.localValue())
                        .bind("remoteValue",conflict.remoteValue())
                        .bind("detectedAt", InstantMapper.toLong(conflict.detectedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(int id) {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM pending_conflicts WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }

    @Override
    public void deleteAll() {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM pending_conflicts")
                        .execute()
        );
    }
}