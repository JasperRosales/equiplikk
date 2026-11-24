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

    private Database() {
        // Prevent object instantiation.
    }

    public static Connection getConnection() throws SQLException {

        DatabaseConfig config = DatabaseConfig.current();

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
