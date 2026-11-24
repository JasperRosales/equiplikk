package src.ui;

import javax.swing.JFrame;
import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * System-tray owner that keeps the desktop process reachable after its window is
 * closed. Minimize, close, exit, and logout remain separate actions.
 */
public final class BackgroundTray {

    private final Runnable openAction;
    private final Runnable exitAction;
    private final Runnable logoutAction;

    private JFrame frame;
    private TrayIcon trayIcon;
    private boolean trayInstalled;

    public BackgroundTray(Runnable openAction, Runnable exitAction, Runnable logoutAction) {
        this.openAction = openAction;
        this.exitAction = exitAction;
        this.logoutAction = logoutAction;
    }

    public void attach(JFrame frame) {
        this.frame = frame;
        if (!SystemTray.isSupported() || trayInstalled) {
            return;
        }
        PopupMenu menu = new PopupMenu("EquipLink");
        add(menu, "Open EquipLink", openAction);
        add(menu, "Exit Application", exitAction);
        add(menu, "Logout", logoutAction);

        trayIcon = new TrayIcon(trayImage(), "EquipLink is running in the background", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> openAction.run());
        try {
            SystemTray.getSystemTray().add(trayIcon);
            trayInstalled = true;
        } catch (AWTException ignored) {
            trayIcon = null;
        }
    }

    private static void add(PopupMenu menu, String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    /** Close Window: hide without logging out when a tray is available. */
    public void closeWindow() {
        if (frame == null) {
            return;
        }
        if (trayInstalled) {
            frame.setVisible(false);
            showMessage("EquipLink is still running",
                    "Notifications continue in the background. Use the tray icon to reopen.");
        } else {
            // Without a tray there is no reliable way back into a hidden window, so
            // minimize instead. The session and notifications keep running either way.
            frame.setState(JFrame.ICONIFIED);
        }
    }

    public void minimizeWindow() {
        if (frame != null) {
            frame.setState(JFrame.ICONIFIED);
        }
    }

    public void openWindow() {
        if (frame == null) {
            openAction.run();
            return;
        }
        frame.setState(JFrame.NORMAL);
        frame.setVisible(true);
        frame.toFront();
        frame.requestFocus();
    }

    public void showMessage(String title, String message) {
        if (trayInstalled && trayIcon != null && title != null && message != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    public void dispose() {
        if (trayInstalled && trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        trayIcon = null;
        trayInstalled = false;
        frame = null;
    }

    private static Image trayImage() {
        javax.swing.ImageIcon icon = BrandLogo.icon(32);
        if (icon != null && icon.getIconWidth() > 0) {
            return icon.getImage();
        }
        BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fallback.setRGB(0, 0, 16, 16,
                new int[256], 0, 16);
        return fallback;
    }
}
