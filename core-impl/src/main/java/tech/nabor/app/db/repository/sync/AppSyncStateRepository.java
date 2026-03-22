// core-impl/src/main/java/tech/nabor/app/db/repository/sync/AppSyncStateRepository.java
package tech.nabor.app.db.repository.sync;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.sync.SyncState;
import tech.nabor.api.repository.sync.SyncStateRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class AppSyncStateRepository implements SyncStateRepository {

    private final Jdbi jdbi;

    public AppSyncStateRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class SyncStateMapper implements RowMapper<SyncState> {
        @Override
        public SyncState map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SyncState(
                    InstantMapper.fromNullableLong(rs, "last_synced_at"),
                    rs.getString("last_sync_token"),
                    rs.getInt("is_rolling_back") == 1
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<SyncState> get() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_state WHERE id = 1")
                        .map(new SyncStateMapper())
                        .findOne()
        );
    }

    @Override
    public void save(SyncState state) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO sync_state (id, last_synced_at, last_sync_token, is_rolling_back)
                VALUES (1, :lastSyncedAt, :lastSyncToken, :isRollingBack)
                ON CONFLICT(id) DO UPDATE SET
                    last_synced_at  = excluded.last_synced_at,
                    last_sync_token = excluded.last_sync_token,
                    is_rolling_back = excluded.is_rolling_back
                """)
                        .bind("lastSyncedAt",  InstantMapper.toLong(state.lastSyncedAt()))
                        .bind("lastSyncToken", state.lastSyncToken())
                        .bind("isRollingBack", state.isRollingBack() ? 1 : 0)
                        .execute()
        );
    }

    @Override
    public void updateLastSyncedAt(Instant syncedAt) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                UPDATE sync_state SET last_synced_at = :syncedAt WHERE id = 1
                """)
                        .bind("syncedAt", InstantMapper.toLong(syncedAt))
                        .execute()
        );
    }

    @Override
    public void updateSyncToken(String token) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE sync_state SET last_sync_token = :token WHERE id = 1")
                        .bind("token", token)
                        .execute()
        );
    }

    @Override
    public void setRollingBack(boolean rollingBack) {
        jdbi.useHandle(h ->
                h.createUpdate("UPDATE sync_state SET is_rolling_back = :val WHERE id = 1")
                        .bind("val", rollingBack ? 1 : 0)
                        .execute()
        );
    }
}