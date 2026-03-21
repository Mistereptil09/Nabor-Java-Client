package tech.nabor.app.db;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import tech.nabor.api.I18n;
import tech.nabor.api.error.NaborException;

import java.io.IOException;

public class DatabaseManager {

    private final Jdbi jdbi;
    private final I18n i18n;

    public DatabaseManager(String dbPath, I18n i18n) {
        // ":memory:" = base in memory for the test
        this.i18n = i18n;
        jdbi = Jdbi.create("jdbc:sqlite:" + dbPath);
        jdbi.installPlugin(new SqlObjectPlugin());
        initSchema();
    }

    public Jdbi getJdbi() { return jdbi; }

    private void initSchema() {
        jdbi.useHandle(handle -> {
            // activate the foreign keys
            handle.execute("PRAGMA foreign_keys = ON");
            handle.execute(loadSql("schema.sql"));
        });
    }

    private String loadSql(String filename) {
        try (var stream = getClass().getResourceAsStream("/db/" + filename)) {
            if (stream == null) {
                throw new NaborException(
                        NaborException.Kind.DB_ERROR,
                        i18n.t("db.error.sql_not_found", filename),
                        null
                );
            }
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            throw new NaborException(
                    NaborException.Kind.DB_ERROR,
                    i18n.t("db.error.sql_read", filename),
                    e
            );
        }
    }
}