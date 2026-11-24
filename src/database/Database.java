package src.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    /**
     * All timestamps and dates are handled as Philippine time (Asia/Manila, UTC+8).
     */
    public static final String TIME_ZONE = "Asia/Manila";

    /** Seconds to wait for a PostgreSQL connection before surfacing a database error. */
    private static final int LOGIN_TIMEOUT_SECONDS = 10;

    private Database() {
        // Prevent object instantiation.
    }

    public static Connection getConnection() throws SQLException {

        DatabaseConfig config = DatabaseConfig.current();
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);

        Connection connection = DriverManager.getConnection(
                config.jdbcUrl(),
                config.user(),
                config.password());

        // CURRENT_TIMESTAMP, CURRENT_DATE, and timestamptz rendering then follow
        // Philippine time no matter how the server itself is configured.
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TIME ZONE '" + TIME_ZONE + "'");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }

        return connection;
    }
}
