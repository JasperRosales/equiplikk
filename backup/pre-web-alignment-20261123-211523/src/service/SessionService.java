package src.service;

import src.database.Database;
import src.model.User;
import src.util.NotificationSound;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Backed persistent login sessions.
 *
 * <p>The browser-style token is random and is never stored in the database. Only
 * its SHA-256 digest is saved, together with the account id, so a token stolen
 * from one local file cannot be exchanged for a different user's session. Every
 * resume validates the row, expiry, and current account status in PostgreSQL.</p>
 */
public final class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_DAYS = 30;
    private static final String USER_COLUMNS = """
            u.user_id, u.username, u.email, u.full_name, u.department,
            u.role, u.borrower_type, u.active, u.notifications_enabled,
            u.notification_sound_enabled, u.notification_sound,
            u.profile_picture, u.created_at, u.last_login
            """;

    private SessionService() {
    }

    /** Creates a new token for one account and invalidates that account's old tokens. */
    public static String createSession(long userId) throws SQLException {
        String token = generateToken();
        String hash = hash(token);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp expires = Timestamp.valueOf(LocalDateTime.now().plusDays(SESSION_DAYS));

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT 1 FROM users
                        WHERE user_id = ? AND active = TRUE AND deleted_at IS NULL
                        """)) {
                    statement.setLong(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("This account can no longer sign in.");
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE user_sessions SET revoked_at = ? "
                                + "WHERE user_id = ? AND revoked_at IS NULL")) {
                    statement.setTimestamp(1, now);
                    statement.setLong(2, userId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO user_sessions
                            (user_id, token_hash, created_at, expires_at, last_seen_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, userId);
                    statement.setString(2, hash);
                    statement.setTimestamp(3, now);
                    statement.setTimestamp(4, expires);
                    statement.setTimestamp(5, now);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
        return token;
    }

    /**
     * Validates a saved token and returns its account, or {@code null} when the
     * token is unknown, revoked, expired, or belongs to a deleted/disabled account.
     */
    public static User resumeSession(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return null;
        }
        String hash = hash(token);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        // Keep an explicit space after SELECT: Java text blocks strip the trailing
        // space before a closing delimiter, which would otherwise produce SELECTu.
        String sql = "SELECT " + USER_COLUMNS + """
                FROM user_sessions s
                JOIN users u ON u.user_id = s.user_id
                WHERE s.token_hash = ?
                  AND s.revoked_at IS NULL
                  AND s.expires_at > ?
                  AND u.active = TRUE
                  AND u.deleted_at IS NULL
                """;
        try (Connection connection = Database.getConnection()) {
            User user;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, hash);
                statement.setTimestamp(2, now);
                try (ResultSet result = statement.executeQuery()) {
                    user = result.next() ? mapUser(result) : null;
                }
            }
            if (user != null) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE user_sessions
                        SET last_seen_at = ?, expires_at = ?
                        WHERE token_hash = ? AND revoked_at IS NULL
                        """)) {
                    statement.setTimestamp(1, now);
                    statement.setTimestamp(2,
                            Timestamp.valueOf(LocalDateTime.now().plusDays(SESSION_DAYS)));
                    statement.setString(3, hash);
                    statement.executeUpdate();
                }
            }
            return user;
        }
    }

    /** Fast status check used by the background monitor without replacing UI user state. */
    public static boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String sql = """
                SELECT 1
                FROM user_sessions s
                JOIN users u ON u.user_id = s.user_id
                WHERE s.token_hash = ?
                  AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP
                  AND u.active = TRUE
                  AND u.deleted_at IS NULL
                LIMIT 1
                """;
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash(token));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            // A transient connection failure must not be treated as a revoked login.
            return true;
        }
    }

    /** Invalidates one saved token. Safe to call when the token is already gone. */
    public static void revokeSession(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE user_sessions
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE token_hash = ? AND revoked_at IS NULL
                        """)) {
            statement.setString(1, hash(token));
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // Local logout must still finish when the database is briefly unavailable.
        }
    }

    /** Invalidates every saved session for an account (used when an account is closed). */
    public static void revokeAllForUser(long userId) {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE user_sessions
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND revoked_at IS NULL
                        """)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        } catch (SQLException ignored) {
            // Authorization checks still happen against users on every resume.
        }
    }

    private static User mapUser(ResultSet result) throws SQLException {
        User user = new User(
                result.getLong("user_id"),
                result.getString("username"),
                result.getString("email"),
                result.getString("full_name"),
                result.getString("department"),
                result.getString("role"),
                result.getString("borrower_type"),
                result.getBoolean("active"));
        user.setNotificationsEnabled(result.getBoolean("notifications_enabled"));
        user.setNotificationSoundEnabled(result.getBoolean("notification_sound_enabled"));
        user.setNotificationSound(
                NotificationSound.normalize(result.getString("notification_sound")));
        user.setProfilePicture(result.getBytes("profile_picture"));
        Timestamp created = result.getTimestamp("created_at");
        Timestamp lastLogin = result.getTimestamp("last_login");
        user.setCreatedAt(created == null ? null : created.toLocalDateTime());
        user.setLastLogin(lastLogin == null ? null : lastLogin.toLocalDateTime());
        return user;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                text.append(String.format("%02x", value & 0xff));
            }
            return text.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not secure the login session.", exception);
        }
    }
}
