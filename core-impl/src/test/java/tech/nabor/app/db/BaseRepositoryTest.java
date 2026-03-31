package tech.nabor.app.db;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import tech.nabor.app.TestI18n;

import java.io.File;
import java.io.IOException;

public abstract class BaseRepositoryTest {

    protected Jdbi jdbi;

    @BeforeEach
    void setUpDatabase() throws IOException {
        File tmpDb = File.createTempFile("nabor_test_", ".db");
        tmpDb.deleteOnExit();
        DatabaseManager db = new DatabaseManager(tmpDb.getAbsolutePath(), new TestI18n());
        jdbi = db.getJdbi();
    }
}