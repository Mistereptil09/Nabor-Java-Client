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
                        rs.getString("base_updated_at"),
                        InstantMapper.fromNullableLong(rs, "changed_at")
                );
            } catch (Exception e) {
                throw new SQLException("Error mapping SyncChange", e);
            }
        }
    }

    private <T> T parseJson(String raw, TypeReference<T> type) throws Exception {
        if (raw == null) return null;
        return json.readValue(raw, type);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<SyncChange> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_changelog ORDER BY changed_at ASC")
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
    public java.util.Optional<SyncChange> findById(int id) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_changelog WHERE id = :id")
                        .bind("id", id)
                        .map(new SyncChangeMapper())
                        .findOne()
        );
    }

    @Override
    public void track(ChangeEvent event) {
        jdbi.useTransaction(h -> {
            try {
                // Merge with existing entry for same table+row: keep original previous_values,
                // merge new_values and changed_fields, update changed_at.
                var existing = h.createQuery(
                        "SELECT * FROM sync_changelog WHERE table_name = :t AND row_id = :r")
                        .bind("t", event.tableName()).bind("r", event.rowId())
                        .map(new SyncChangeMapper()).findOne();

                Map<String, String> prev = event.previousValues();
                Map<String, String> next = event.newValues();

                if (existing.isPresent()) {
                    SyncChange old = existing.get();
                    if (old.previousValues() != null) {
                        Map<String, String> merged = new java.util.HashMap<>(old.previousValues());
                        if (event.previousValues() != null) merged.putAll(event.previousValues());
                        prev = merged;
                    }
                    if (old.newValues() != null) {
                        Map<String, String> merged = new java.util.HashMap<>(old.newValues());
                        if (event.newValues() != null) merged.putAll(event.newValues());
                        next = merged;
                    }
                    h.createUpdate("DELETE FROM sync_changelog WHERE id = :id")
                            .bind("id", old.id()).execute();
                }

                final Map<String, String> finalPrev = prev;
                final Map<String, String> finalNext = next;
                final List<String> fields;
                if (finalNext != null && finalPrev != null) {
                    fields = finalNext.keySet().stream()
                            .filter(k -> !finalNext.get(k).equals(finalPrev.getOrDefault(k, "")))
                            .toList();
                } else if (finalNext != null) {
                    fields = List.copyOf(finalNext.keySet());
                } else {
                    fields = null;
                }

                h.createUpdate("""
                    INSERT INTO sync_changelog
                        (table_name, row_id, operation, changed_fields,
                         previous_values, new_values, base_updated_at, changed_at)
                    VALUES
                        (:tableName, :rowId, :operation, :changedFields,
                         :previousValues, :newValues, :baseUpdatedAt, :changedAt)
                    """)
                        .bind("tableName",      event.tableName())
                        .bind("rowId",          event.rowId())
                        .bind("operation",      event.operation())
                        .bind("changedFields",  fields != null ? json.writeValueAsString(fields) : null)
                        .bind("previousValues", finalPrev != null ? json.writeValueAsString(finalPrev) : null)
                        .bind("newValues",      finalNext != null ? json.writeValueAsString(finalNext) : null)
                        .bind("baseUpdatedAt",  event.baseUpdatedAt())
                        .bind("changedAt",      InstantMapper.toLong(event.occurredAt()))
                        .execute();
            } catch (Exception e) {
                throw new RuntimeException("Error tracking ChangeEvent", e);
            }
        });
    }

    @Override
    public void deleteByTableAndRow(String tableName, String rowId) {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM sync_changelog WHERE table_name = :t AND row_id = :r")
                        .bind("t", tableName)
                        .bind("r", rowId)
                        .execute()
        );
    }

    @Override
    public void deleteAll() {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM sync_changelog")
                        .execute()
        );
    }

    @Override
    public void deleteById(int id) {
        jdbi.useHandle(h ->
                h.createUpdate("DELETE FROM sync_changelog WHERE id = :id")
                        .bind("id", id)
                        .execute()
        );
    }
}
