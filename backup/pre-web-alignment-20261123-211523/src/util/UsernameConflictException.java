package src.util;

/** Raised when a deleted account cannot be restored because its username is taken. */
public class UsernameConflictException extends Exception {

    public static final String TITLE = "Username Conflict";
    public static final String MESSAGE =
            "This username is already used by another account. "
                    + "Resolve the username conflict before restoring this account.";

    public UsernameConflictException() {
        super(MESSAGE);
    }
}
