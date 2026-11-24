package src.controller;

import src.model.PasswordResetRequest;
import src.model.User;
import src.service.AuthService;

import java.util.List;

public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // LOGIN
    public User login(String username, char[] password)
            throws Exception {

        return authService.login(username, password);
    }

    // REGISTRATION
    public void registerBorrower(
            String username,
            String email,
            String fullName,
            String department,
            String borrowerType,
            char[] password) throws Exception {

        authService.registerBorrower(
                username,
                email,
                fullName,
                department,
                borrowerType,
                password);
    }

    public void createFacultyAccount(
            User actor,
            String username,
            String email,
            String fullName,
            String department,
            char[] password) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in account is required.");
        }
        authService.createFacultyAccount(
                actor.getRole(), username, email, fullName, department, password);
    }

    public void changePassword(
            long userId,
            char[] currentPassword,
            char[] newPassword) throws Exception {

        authService.changePassword(userId, currentPassword, newPassword);
    }

    /** Saves permitted profile fields for the signed-in user only. */
    public void updateOwnProfile(
            User actor, String username, String fullName, String department,
            byte[] profilePicture) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in account is required.");
        }
        authService.updateOwnProfile(
                actor.getUserId(), username, fullName, department, profilePicture);
    }

    public void updateNotificationPreference(long userId, boolean enabled) throws Exception {
        authService.updateNotificationPreference(userId, enabled);
    }

    /** Saves the signed-in user's notification sound choice. */
    public void updateNotificationSoundPreference(
            User actor, boolean enabled, String sound) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in account is required.");
        }
        authService.updateNotificationSoundPreference(actor.getUserId(), enabled, sound);
    }

    public void requestPasswordReset(String usernameOrEmail, String registeredEmail) throws Exception {
        authService.requestPasswordReset(usernameOrEmail, registeredEmail);
    }

    public void resetPasswordAfterApproval(
            String usernameOrEmail, String registeredEmail, char[] newPassword) throws Exception {
        authService.resetPasswordAfterApproval(usernameOrEmail, registeredEmail, newPassword);
    }

    public List<PasswordResetRequest> findPasswordResetRequests(User actor)
            throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in authorized account is required.");
        }
        return authService.findPasswordResetRequests(actor.getRole());
    }

    public void reviewPasswordReset(
            User actor, long requestId, boolean approve, String note) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in authorized account is required.");
        }
        authService.reviewPasswordReset(
                actor.getRole(), actor.getUserId(), requestId, approve, note);
    }

    public List<User> findBorrowersForIct(User actor) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        return authService.findBorrowersForIct(actor.getRole());
    }

    /**
     * Deactivate or reactivate a borrower account while preserving all of its
     * history.
     */
    public void setBorrowerActive(User actor, long userId, boolean active) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        authService.setBorrowerActive(actor.getRole(), actor.getUserId(), userId, active);
    }

    /** Soft-deletes a borrower account; no account or historical row is removed. */
    public java.time.LocalDateTime deleteBorrowerAccount(User actor, long userId)
            throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        return authService.deleteBorrowerAccount(actor.getRole(), actor.getUserId(), userId);
    }

    /**
     * Restores a soft-deleted borrower in place, preserving its id and all history.
     */
    public void restoreBorrowerAccount(User actor, long userId) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in ICT account is required.");
        }
        authService.restoreBorrowerAccount(actor.getRole(), actor.getUserId(), userId);
    }

}