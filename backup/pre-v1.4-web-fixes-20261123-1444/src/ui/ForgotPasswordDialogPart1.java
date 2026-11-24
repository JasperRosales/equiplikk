
package src.ui;

import src.controller.AuthController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Part 1 of ForgotPasswordDialog, split out to keep files short. */
public abstract class ForgotPasswordDialogPart1 extends JDialog {

    protected ForgotPasswordDialogPart1(java.awt.Window owner, String title, java.awt.Dialog.ModalityType modality) {
        super(owner, title, modality);
    }

    AuthController authController;

    final JTextField identity = new SoftTextField();
    final JTextField registeredEmail = new SoftTextField();
    final JPasswordField newPassword = new SoftPasswordField();
    final JPasswordField confirmPassword = new SoftPasswordField();

    final Soft3DButton requestButton =
            new Soft3DButton("Request ICT approval", true);

    final Soft3DButton resetButton =
            new Soft3DButton("Set new password", false);

}
