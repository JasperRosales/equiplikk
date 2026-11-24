package src.util;

/** Raised when a username is already used by another account. */
public class UsernameAlreadyExistsException extends Exception {

    public static final String TITLE = "Username Already Exists";
    public static final String MESSAGE =
            "This username is already taken. Please choose another username.";

    public UsernameAlreadyExistsException() {
        super(MESSAGE);
    }
}
