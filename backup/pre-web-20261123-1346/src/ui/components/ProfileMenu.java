package src.ui.components;

import src.model.User;
import src.ui.AppTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Upper-right account control shared by every role. The avatar, name, role and
 * arrow all open the same menu; the menu itself only displays data from the
 * authenticated {@link User} and delegates actions to the owning layout.
 */
public class ProfileMenu extends JPanel {

    private final User user;
    private final String roleLabel;
    private final Runnable profileAction;
    private final Runnable settingsAction;
    private final Runnable passwordAction;
    private final Runnable logoutAction;
    private final Runnable onThemeChanged;

    private final JPopupMenu popup = new JPopupMenu();
    private final JPanel identityCard = new JPanel(new BorderLayout(11, 0));
    private final Avatar cardAvatar = new Avatar("EL", AppTheme.primary(), 999);
    private final JLabel cardName = new JLabel();
    private final JLabel cardEmail = new JLabel();
    private final JLabel cardRole = new JLabel();
    private final Avatar anchorAvatar = new Avatar("EL", AppTheme.primary(), 999);
    private final JLabel anchorName = new JLabel();
    private final JLabel anchorRole = new JLabel();
    private final JLabel arrow = new JLabel();
    private JRadioButtonMenuItem lightMode;
    private JRadioButtonMenuItem darkMode;

    public ProfileMenu(
            User user,
            String roleLabel,
            Runnable onProfile,
            Runnable onSettings,
            Runnable onPassword,
            Runnable onThemeChanged,
            Runnable onLogout) {

        this.user = user;
        this.roleLabel = roleLabel == null || roleLabel.isBlank() ? "Account" : roleLabel;
        this.profileAction = onProfile;
        this.settingsAction = onSettings;
        this.passwordAction = onPassword;
        this.logoutAction = onLogout;
        this.onThemeChanged = onThemeChanged;

        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        setBorder(new EmptyBorder(0, 0, 0, 0));

        anchorAvatar.setPreferredSize(new Dimension(34, 34));
        anchorAvatar.setToolTipText("Account menu");

        JPanel identity = new JPanel(new GridLayout(0, 1, 0, 1));
        identity.setOpaque(false);
        anchorName.setFont(AppTheme.semibold(12));
        anchorRole.setFont(AppTheme.regular(10.5f));
        identity.add(anchorName);
        identity.add(anchorRole);

        // A drawn icon avoids font-dependent glyphs (which can render as a box).
        arrow.setIcon(LucideIcon.forChevron(12));
        arrow.setBorder(new EmptyBorder(0, 1, 0, 0));

        add(anchorAvatar);
        add(identity);
        add(arrow);
        setToolTipText("Open account menu");

        buildPopup();
        refresh();
        attachClick(this);
    }

    private void buildPopup() {
        popup.setBorder(new EmptyBorder(4, 4, 4, 4));
        identityCard.setOpaque(true);
        identityCard.setBackground(AppTheme.surface());
        identityCard.setBorder(new EmptyBorder(10, 12, 10, 12));
        cardAvatar.setPreferredSize(new Dimension(36, 36));
        cardAvatar.setFont(AppTheme.semibold(12));
        cardName.setFont(AppTheme.semibold(12.5f));
        cardEmail.setFont(AppTheme.regular(10.5f));
        cardRole.setFont(AppTheme.regular(10));
        cardRole.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.add(cardName);
        details.add(Box.createVerticalStrut(3));
        details.add(cardEmail);
        details.add(cardRole);
        identityCard.add(cardAvatar, BorderLayout.WEST);
        identityCard.add(details, BorderLayout.CENTER);

        popup.add(identityCard);
        popup.add(new JSeparator());
        popup.add(menuItem("My Profile", () -> {
            popup.setVisible(false);
            runIfSignedIn(profileAction);
        }));
        popup.add(menuItem("Account Settings", () -> {
            popup.setVisible(false);
            runIfSignedIn(settingsAction);
        }));
        popup.add(menuItem("Change Password", () -> {
            popup.setVisible(false);
            runIfSignedIn(passwordAction);
        }));
        popup.add(appearanceMenu());
        popup.add(new JSeparator());
        JMenuItem logout = menuItem("Logout", () -> {
            popup.setVisible(false);
            run(logoutAction);
        });
        logout.setForeground(AppTheme.danger());
        popup.add(logout);
    }

    private JMenuItem menuItem(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        styleMenuItem(item);
        item.addActionListener(event -> action.run());
        return item;
    }

    private static void styleMenuItem(JMenuItem item) {
        item.setFont(AppTheme.regular(12));
        item.setForeground(AppTheme.foreground());
        item.setBorder(new EmptyBorder(6, 12, 6, 12));
    }

    private JMenu appearanceMenu() {
        JMenu appearance = new JMenu("Appearance");
        styleMenuItem(appearance);
        lightMode = new JRadioButtonMenuItem("Light Mode");
        darkMode = new JRadioButtonMenuItem("Dark Mode");
        styleMenuItem(lightMode);
        styleMenuItem(darkMode);
        ButtonGroup group = new ButtonGroup();
        group.add(lightMode);
        group.add(darkMode);
        lightMode.addActionListener(event -> {
            if (AppTheme.isDarkMode()) {
                AppTheme.setDarkMode(false);
                run(onThemeChanged);
            }
        });
        darkMode.addActionListener(event -> {
            if (!AppTheme.isDarkMode()) {
                AppTheme.setDarkMode(true);
                run(onThemeChanged);
            }
        });
        appearance.add(lightMode);
        appearance.add(darkMode);
        return appearance;
    }

    /** Refreshes all identity labels from the current authenticated user object. */
    public void refresh() {
        String name = user == null || user.getFullName() == null
                ? "EquipLink user" : user.getFullName();
        String email = user == null || user.getEmail() == null ? "—" : user.getEmail();
        byte[] picture = user == null ? null : user.getProfilePicture();

        anchorName.setText(name);
        anchorRole.setText(roleLabel);
        cardName.setText(name);
        cardEmail.setText(email);
        cardRole.setText(roleLabel);
        anchorAvatar.setIdentity(name, picture);
        cardAvatar.setIdentity(name, picture);
        applyTheme();
    }

    /** Called by the layout after a global theme change. */
    public void applyTheme() {
        identityCard.setBackground(AppTheme.surface());
        cardName.setForeground(AppTheme.foreground());
        cardEmail.setForeground(AppTheme.muted());
        // The maroon card role needs a light tint in dark mode to stay readable.
        cardRole.setForeground(AppTheme.isDarkMode()
                ? AppTheme.muted() : AppTheme.primary());
        anchorName.setForeground(AppTheme.foreground());
        anchorRole.setForeground(AppTheme.muted());
        arrow.setForeground(AppTheme.muted());
        popup.setBackground(AppTheme.surface());
        popup.setForeground(AppTheme.foreground());
        rethemeMenuItems(popup);
        if (lightMode != null) {
            lightMode.setSelected(!AppTheme.isDarkMode());
        }
        if (darkMode != null) {
            darkMode.setSelected(AppTheme.isDarkMode());
        }
        revalidate();
        repaint();
    }

    private static void rethemeMenuItems(java.awt.Container container) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JMenuItem item) {
                item.setForeground(AppTheme.foreground());
            }
            if (component instanceof JMenu menu) {
                rethemeMenuItems(menu);
            }
        }
    }

    public JLabel getNameLabel() {
        return anchorName;
    }

    public JLabel getRoleLabel() {
        return anchorRole;
    }

    private void runIfSignedIn(Runnable action) {
        if (user != null) {
            run(action);
        }
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    /** Makes the avatar, name, role, and chevron all open the same popup. */
    private void attachClick(JComponent component) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                popup.show(ProfileMenu.this, 0, ProfileMenu.this.getHeight() + 6);
                event.consume();
            }
        });
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent jchild) {
                attachClick(jchild);
            }
        }
    }
}
