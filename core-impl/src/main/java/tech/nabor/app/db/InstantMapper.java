package tech.nabor.app.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public final class InstantMapper {

    private InstantMapper() {}

    public static Instant fromNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    public static Long toLong(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}