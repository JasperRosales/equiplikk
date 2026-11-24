package src.util;

/** Raised at login when an account was deleted by the ICT System Administrator. */
public class AccountDeletedException extends Exception {

    public static final String TITLE = "Account Deleted";
    public static final String MESSAGE =
            "Your account has been deleted by the ICT System Administrator. "
                    + "You can no longer log in to EquipLink. Please contact the ICT "
                    + "System Administrator for assistance.";

    public AccountDeletedException() {
        super(MESSAGE);
    }
}
