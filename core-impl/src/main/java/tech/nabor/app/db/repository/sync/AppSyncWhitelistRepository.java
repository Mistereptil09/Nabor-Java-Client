package tech.nabor.app.db.repository.sync;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tech.nabor.api.model.sync.SyncWhitelist;
import tech.nabor.api.repository.sync.SyncWhitelistRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AppSyncWhitelistRepository implements SyncWhitelistRepository {

    private final Jdbi jdbi;

    public AppSyncWhitelistRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    private static class Mapper implements RowMapper<SyncWhitelist> {
        @Override
        public SyncWhitelist map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new SyncWhitelist(
                    rs.getString("entity_type"),
                    rs.getString("field_name")
            );
        }
    }

    @Override
    public List<SyncWhitelist> findByType(String entityType) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_whitelist WHERE entity_type = :type ORDER BY field_name")
                        .bind("type", entityType)
                        .map(new Mapper())
                        .list()
        );
    }

    @Override
    public List<SyncWhitelist> findAll() {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM sync_whitelist ORDER BY entity_type, field_name")
                        .map(new Mapper())
                        .list()
        );
    }

    @Override
    public void replaceAll(String entityType, List<String> fields) {
        jdbi.useTransaction(h -> {
            h.createUpdate("DELETE FROM sync_whitelist WHERE entity_type = :type")
                    .bind("type", entityType)
                    .execute();
            for (String field : fields) {
                h.createUpdate("INSERT INTO sync_whitelist (entity_type, field_name) VALUES (:type, :field)")
                        .bind("type",  entityType)
                        .bind("field", field)
                        .execute();
            }
        });
    }
}
