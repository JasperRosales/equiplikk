package web.service;

import src.database.Database;
import src.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

/** Read-only user lookup used to re-validate the signed-in account on every request. */
public final class WebUserRepository {

    private WebUserRepository() {
    }

    public static User findById(long userId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT user_id, username, email, full_name, department, role,
                            borrower_type, active, deleted_at,
                            notifications_enabled, notification_sound_enabled,
                            notification_sound, profile_picture, created_at, last_login
                     FROM users WHERE user_id = ?
                     """)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                User user = new User(
                        result.getLong("user_id"),
                        result.getString("username"),
                        result.getString("email"),
                        result.getString("full_name"),
                        result.getString("department"),
                        result.getString("role"),
                        result.getString("borrower_type"),
                        result.getBoolean("active"));
                user.setDeletedAt(toLocal(result.getTimestamp("deleted_at")));
                user.setNotificationsEnabled(result.getBoolean("notifications_enabled"));
                user.setNotificationSoundEnabled(result.getBoolean("notification_sound_enabled"));
                user.setNotificationSound(result.getString("notification_sound"));
                user.setProfilePicture(result.getBytes("profile_picture"));
                user.setCreatedAt(toLocal(result.getTimestamp("created_at")));
                user.setLastLogin(toLocal(result.getTimestamp("last_login")));
                return user;
            }
        }
    }

    private static java.time.LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
