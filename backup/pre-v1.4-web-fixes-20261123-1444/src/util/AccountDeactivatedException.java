package src.util;

/**
 * Raised by the authentication backend when an account exists but has been
 * deactivated by the ICT System Administrator. Login stops before any
 * authenticated session is created.
 */
public class AccountDeactivatedException extends Exception {

    public static final String TITLE = "Account Deactivated";
    public static final String MESSAGE =
            "Your account has been deactivated by the ICT System Administrator. "
                    + "You cannot access EquipLink. Please contact the ICT System "
                    + "Administrator for assistance.";

    public AccountDeactivatedException() {
        super(MESSAGE);
    }
}
