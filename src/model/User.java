package src.model;

import java.time.LocalDateTime;

public class User {

    private long userId;
    private String username;
    private String email;
    private String fullName;
    private String department;
    private String role;
    private String borrowerType;
    private boolean active;
    /** Set when the ICT System Administrator soft-deletes this account. */
    private LocalDateTime deletedAt;
    private boolean notificationsEnabled = true;
    /** Whether a short sound plays when a genuinely new notification arrives. */
    private boolean notificationSoundEnabled = true;
    /** Selected built-in sound name; unknown values fall back to the default. */
    private String notificationSound = "chime";
    /** Normalized PNG avatar bytes from users.profile_picture; null shows initials. */
    private byte[] profilePicture;
    /** Registration timestamp from users.created_at. */
    private LocalDateTime createdAt;
    /** Most recent successful sign-in, when the column is available. */
    private LocalDateTime lastLogin;

    public User(long userId, String username, String email,
            String fullName, String department,
            String role, String borrowerType, boolean active) {

        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.department = department;
        this.role = role;
        this.borrowerType = borrowerType;
        this.active = active;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDepartment() {
        return department;
    }

    public String getRole() {
        return role;
    }

    public String getBorrowerType() {
        return borrowerType;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public boolean isNotificationSoundEnabled() {
        return notificationSoundEnabled;
    }

    public String getNotificationSound() {
        return notificationSound;
    }

    public byte[] getProfilePicture() {
        return profilePicture;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setBorrowerType(String borrowerType) {
        this.borrowerType = borrowerType;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public void setNotificationSoundEnabled(boolean notificationSoundEnabled) {
        this.notificationSoundEnabled = notificationSoundEnabled;
    }

    public void setNotificationSound(String notificationSound) {
        this.notificationSound = notificationSound == null || notificationSound.isBlank()
                ? "chime" : notificationSound;
    }

    public void setProfilePicture(byte[] profilePicture) {
        this.profilePicture = profilePicture;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
}