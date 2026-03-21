// core-impl/src/main/java/tech/nabor/app/db/repository/local/AppPluginStateRepository.java
package tech.nabor.app.db.repository.local;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.local.PluginState;
import tech.nabor.api.repository.local.PluginStateRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppPluginStateRepository implements PluginStateRepository {

    private final Jdbi jdbi;

    public AppPluginStateRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class PluginStateMapper implements RowMapper<PluginState> {
        @Override
        public PluginState map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PluginState(
                    rs.getString("user_id"),
                    rs.getString("plugin_id"),
                    rs.getInt("enabled") == 1,
                    rs.getInt("display_order"),
                    InstantMapper.fromNullableLong(rs, "updated_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public List<PluginState> findByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM plugin_state WHERE user_id = :userId")
                        .bind("userId", userId)
                        .map(new PluginStateMapper())
                        .list()
        );
    }

    @Override
    public Optional<PluginState> findByUserAndPlugin(String userId, String pluginId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM plugin_state
                WHERE user_id = :userId AND plugin_id = :pluginId
                """)
                        .bind("userId",   userId)
                        .bind("pluginId", pluginId)
                        .map(new PluginStateMapper())
                        .findOne()
        );
    }

    @Override
    public List<PluginState> findEnabledByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("""
                SELECT * FROM plugin_state
                WHERE user_id = :userId AND enabled = 1
                ORDER BY display_order ASC
                """)
                        .bind("userId", userId)
                        .map(new PluginStateMapper())
                        .list()
        );
    }

    @Override
    public void save(PluginState state) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO plugin_state (user_id, plugin_id, enabled, display_order, updated_at)
                VALUES (:userId, :pluginId, :enabled, :displayOrder, :updatedAt)
                ON CONFLICT(user_id, plugin_id) DO UPDATE SET
                    enabled       = excluded.enabled,
                    display_order = excluded.display_order,
                    updated_at    = excluded.updated_at
                """)
                        .bind("userId",       state.userId())
                        .bind("pluginId",     state.pluginId())
                        .bind("enabled",      state.enabled() ? 1 : 0)
                        .bind("displayOrder", state.displayOrder())
                        .bind("updatedAt",    InstantMapper.toLong(state.updatedAt()))
                        .execute()
        );
    }

    @Override
    public void delete(String userId, String pluginId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                DELETE FROM plugin_state
                WHERE user_id = :userId AND plugin_id = :pluginId
                """)
                        .bind("userId",   userId)
                        .bind("pluginId", pluginId)
                        .execute()
        );
    }
}