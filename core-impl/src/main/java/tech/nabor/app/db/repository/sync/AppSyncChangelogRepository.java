package tech.nabor.app.db.repository.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.event.ChangeEvent;
import tech.nabor.api.model.sync.SyncChange;
import tech.nabor.api.repository.sync.SyncChangelogRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class AppSyncChangelogRepository implements SyncChangelogRepository {

    private final Jdbi jdbi;
    private final ObjectMapper json = new ObjectMapper();

    public AppSyncChangelogRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private class SyncChangeMapper implements RowMapper<SyncChange> {
        @Override
        public SyncChange map(ResultSet rs, StatementContext ctx) throws SQLException {
            try {
                List<String> changedFields = parseJson(
                        rs.getString("changed_fields"), new TypeReference<>() {}
                );
                Map<String, String> previousValues = parseJson(
                        rs.getString("previous_values"), new TypeReference<>() {}
                );
                Map<String, String> newValues = parseJson(
                        rs.getString("new_values"), new TypeReference<>() {}
                );
                return new SyncChange(
                        rs.getInt("id"),
                        rs.getString("table_name"),
                        rs.getString("row_id"),
                        rs.getString("operation"),
                        changedFields,
                        previousValues,
                        newValues,
                        InstantMapper.fromNullableLong(rs, "changed_at"),
                        InstantMapper.fromNullableLong(rs, "synced_at")
                );
            } catch (Exception e) {
                throw new SQLException("Erreur mapping SyncChange", e);
            }
        }
    }

    private <T> T parseJson(String raw, TypeReference<T> type) throws Exception {
        if (raw == null) return null;
        return json.readValue(raw, type);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public void track(ChangeEvent event) {
        jdbi.useHandle(h -> {
            try {
                List<String> changedFields = event.newValues() != null && event.previousValues() != null
                        ? event.newValues().keySet().stream()
                        .filter(k -> !event.newValues().get(k).equals(
                                event.previousValues().getOrDefault(k, null)))
                        .toList()
                        : null;

                h.createUpdate("""
                    INSERT INTO sync_changelog
                        (table_name, row_id, operation, changed_fields, previous_values, new_values, changed_at)
                    VALUES
                        (:tableName, :rowId, :operation, :changedFields, :previousValues, :newValues, :changedAt)
                    """)
                        .bind("tableName",      event.tableName())
                        .bind("rowId",          event.rowId())
                        .bind("operation",      event.operation())
                        .bind("changedFields",  changedFields != null ? json.writeValueAsString(changedFields) : null)
                        .bind("previousValues", event.previousValues() != null ? json.writeValueAsString(event.previousValues()) : null)
                        .bind("newValues",      event.newValues() != null ? json.writeValueAsString(event.newValues()) : null)
                        .bind("changedAt",      InstantMapper.toLong(event.occurredAt()))
                        .execute();
            } catch (Exception e) {
                throw new RuntimeException("Erreur track ChangeEvent", e);
            }
        });
    }

    @Override
    public List<SyncChange> findUnsynced() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_changelog WHERE synced_at IS NULL ORDER BY changed_at ASC")
                        .map(new SyncChangeMapper())
                        .list()
        );
    }

    @Override
    public List<SyncChange> findByTable(String tableName) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_changelog WHERE table_name = :tableName ORDER BY changed_at ASC")
                        .bind("tableName", tableName)
                        .map(new SyncChangeMapper())
                        .list()
        );
    }

    @Override
    public void markSynced(int id) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE sync_changelog SET synced_at = :now WHERE id = :id")
                        .bind("now", System.currentTimeMillis())
                        .bind("id",  id)
                        .execute()
        );
    }

    @Override
    public void markAllSynced() {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE sync_changelog SET synced_at = :now WHERE synced_at IS NULL")
                        .bind("now", System.currentTimeMillis())
                        .execute()
        );
    }

    @Override
    public void deleteUnsynced() {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM sync_changelog WHERE synced_at IS NULL")
                        .execute()
        );
    }
}