package tech.nabor.app.db.repository.sync;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.sync.SyncState;
import tech.nabor.api.repository.sync.SyncStateRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppSyncStateRepository implements SyncStateRepository {

    private final Jdbi jdbi;

    public AppSyncStateRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    private static class Mapper implements RowMapper<SyncState> {
        @Override
        public SyncState map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SyncState(
                    rs.getString("latest_sync_cursor"),
                    rs.getString("resume_cursor"),
                    rs.getInt("is_rolling_back") == 1
            );
        }
    }

    @Override
    public Optional<SyncState> get() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_state WHERE id = 1")
                        .map(new Mapper())
                        .findOne()
        );
    }

    @Override
    public void save(SyncState state) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO sync_state (id, latest_sync_cursor, resume_cursor, is_rolling_back)
                VALUES (1, :latest, :resume, :rollingBack)
                ON CONFLICT(id) DO UPDATE SET
                    latest_sync_cursor = excluded.latest_sync_cursor,
                    resume_cursor      = excluded.resume_cursor,
                    is_rolling_back    = excluded.is_rolling_back
                """)
                        .bind("latest",      state.latestSyncCursor())
                        .bind("resume",      state.resumeCursor())
                        .bind("rollingBack", state.isRollingBack() ? 1 : 0)
                        .execute()
        );
    }

    @Override
    public void updateLatestCursor(String cursor) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO sync_state (id, latest_sync_cursor, resume_cursor, is_rolling_back)
                VALUES (1, :c, NULL, 0)
                ON CONFLICT(id) DO UPDATE SET
                    latest_sync_cursor = excluded.latest_sync_cursor,
                    resume_cursor      = NULL
                """)
                        .bind("c", cursor)
                        .execute()
        );
    }

    @Override
    public void updateResumeCursor(String cursor) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO sync_state (id, latest_sync_cursor, resume_cursor, is_rolling_back)
                VALUES (1, NULL, :c, 0)
                ON CONFLICT(id) DO UPDATE SET
                    resume_cursor = excluded.resume_cursor
                """)
                        .bind("c", cursor)
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
