package tech.nabor.app.db;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.sqlite.SQLiteDataSource;
import tech.nabor.api.I18n;
import tech.nabor.api.error.NaborException;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class DatabaseManager {

    private final Jdbi jdbi;
    private final I18n i18n;

    public DatabaseManager(String dbPath, I18n i18n) {
        this.i18n = i18n;

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());
        initSchema();

    }

    public Jdbi getJdbi() { return jdbi; }

    private void initSchema() {
        jdbi.useHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");

            String sql = loadSql("schema.sql");
            String cleaned = Arrays.stream(sql.split("\n"))
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));

            for (String statement : cleaned.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    handle.execute(trimmed);
                }
            }
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