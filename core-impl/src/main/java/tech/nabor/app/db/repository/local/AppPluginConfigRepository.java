// core-impl/src/main/java/tech/nabor/app/db/repository/local/AppPluginConfigRepository.java
package tech.nabor.app.db.repository.local;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.local.PluginConfig;
import tech.nabor.api.repository.local.PluginConfigRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AppPluginConfigRepository implements PluginConfigRepository {

    private final Jdbi jdbi;

    public AppPluginConfigRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class PluginConfigMapper implements RowMapper<PluginConfig> {
        @Override
        public PluginConfig map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PluginConfig(
                    rs.getString("user_id"),
                    rs.getString("plugin_id"),
                    rs.getString("key"),
                    rs.getString("value"),
                    InstantMapper.fromNullableLong(rs, "updated_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<String> getValue(String userId, String pluginId, String key) {
        return jdbi.withHandle(h -> {
            var list = h.createQuery("""
                SELECT value FROM plugin_config
                WHERE user_id = :userId AND plugin_id = :pluginId AND key = :key
                """)
                    .bind("userId",   userId)
                    .bind("pluginId", pluginId)
                    .bind("key",      key)
                    .map((rs, ctx) -> rs.getString("value"))
                    .list();

            if (list.isEmpty()) {
                return Optional.empty();
            }

            return Optional.ofNullable(list.getFirst());
        });
    }

    @Override
    public Map<String, String> getAllForPlugin(String userId, String pluginId) {
        return jdbi.withHandle(h -> {
            Map<String, String> result = new HashMap<>();
            h.createQuery("""
                SELECT user_id, plugin_id, key, value, updated_at FROM plugin_config
                WHERE user_id = :userId AND plugin_id = :pluginId
                """)
                    .bind("userId",   userId)
                    .bind("pluginId", pluginId)
                    .map(new PluginConfigMapper())
                    .forEach(config -> result.put(config.key(), config.value()));
            return result;
        });
    }

    @Override
    public void setValue(String userId, String pluginId, String key, String value) {
        jdbi.useHandle(h -> {
            var update = h.createUpdate("""
            INSERT INTO plugin_config (user_id, plugin_id, key, value, updated_at)
            VALUES (:userId, :pluginId, :key, :value, :updatedAt)
            ON CONFLICT(user_id, plugin_id, key) DO UPDATE SET
                value      = excluded.value,
                updated_at = excluded.updated_at
            """)
                    .bind("userId",    userId)
                    .bind("pluginId",  pluginId)
                    .bind("key",       key)
                    .bind("updatedAt", System.currentTimeMillis());

            if (value == null) {
                update.bindNull("value", java.sql.Types.VARCHAR);
            } else {
                update.bind("value", value);
            }

            update.execute();
        });
    }

    @Override
    public void deleteKey(String userId, String pluginId, String key) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                DELETE FROM plugin_config
                WHERE user_id = :userId AND plugin_id = :pluginId AND key = :key
                """)
                        .bind("userId",   userId)
                        .bind("pluginId", pluginId)
                        .bind("key",      key)
                        .execute()
        );
    }

    @Override
    public void deleteAllForPlugin(String userId, String pluginId) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                DELETE FROM plugin_config
                WHERE user_id = :userId AND plugin_id = :pluginId
                """)
                        .bind("userId",   userId)
                        .bind("pluginId", pluginId)
                        .execute()
        );
    }
}