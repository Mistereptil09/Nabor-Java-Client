package tech.nabor.app.db.repository.local;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.local.AppLocaleConfig;
import tech.nabor.api.repository.local.LocaleConfigRepository;
import tech.nabor.app.db.InstantMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class AppLocaleConfigRepositoryImpl implements LocaleConfigRepository {

    private final Jdbi jdbi;

    public AppLocaleConfigRepositoryImpl(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private static class LocaleConfigMapper implements RowMapper<AppLocaleConfig> {
        @Override
        public AppLocaleConfig map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new AppLocaleConfig(
                    rs.getString("user_id"),
                    rs.getString("locale"),
                    InstantMapper.fromNullableLong(rs, "updated_at")
            );
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public Optional<AppLocaleConfig> findByUserId(String userId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM app_locale_config WHERE user_id = :userId")
                        .bind("userId", userId)
                        .map(new LocaleConfigMapper())
                        .findOne()
        );
    }

    @Override
    public void save(AppLocaleConfig config) {
        jdbi.useHandle(h ->
                h.createUpdate("""
                INSERT INTO app_locale_config (user_id, locale, updated_at)
                VALUES (:userId, :locale, :updatedAt)
                ON CONFLICT(user_id) DO UPDATE SET
                    locale     = excluded.locale,
                    updated_at = excluded.updated_at
                """)
                        .bind("userId",    config.userId())
                        .bind("locale",    config.locale())
                        .bind("updatedAt", InstantMapper.toLong(config.updatedAt()))
                        .execute()
        );
    }
}