package src.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Window;

public final class UiSupport {

    /** Client property used to keep one confirmation/action per button at a time. */
    private static final String ACTION_RUNNING = "equiplink.action.running";

    /** Standard button labels used by every confirmation and message popup. */
    private static final String CONFIRM = "Confirm";
    private static final String CANCEL = "Cancel";
    private static final String OKAY = "Okay";
    private static final String CLOSE = "Close";

    private UiSupport() {
    }

    /**
     * Guards an action button against double-clicks. Returns false when the same
     * button already has a confirmation or background action in progress.
     */
    public static boolean beginAction(AbstractButton button) {
        if (button == null) {
            return true;
        }
        if (Boolean.TRUE.equals(button.getClientProperty(ACTION_RUNNING))) {
            return false;
        }
        button.putClientProperty(ACTION_RUNNING, Boolean.TRUE);
        button.setEnabled(false);
        return true;
    }

    /** Re-enables a guarded button after cancellation, failure, or completion. */
    public static void endAction(AbstractButton button) {
        if (button == null) {
            return;
        }
        button.putClientProperty(ACTION_RUNNING, null);
        SwingUtilities.invokeLater(() -> button.setEnabled(true));
    }

    public static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(new TopAlignedView(content));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppTheme.background());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    /**
     * Puts every enclosing scroll container back at the very top. Page content
     * can be wrapped in more than one scroll pane, so the whole ancestor chain
     * is reset rather than just the first one found.
     */
    public static void scrollToTop(Component content) {
        for (Container parent = content.getParent(); parent != null;
                parent = parent.getParent()) {
            if (parent instanceof JScrollPane scroll) {
                scroll.getViewport().setViewPosition(new Point(0, 0));
            }
        }
    }

    /**
     * Swing defers layout, focus and validation work to later EDT passes, and
     * a JViewport can move again when that work completes. Deferring the reset
     * makes it run after those passes instead of being overwritten by them.
     */
    public static void scrollToTopAfterLayout(Component content) {
        SwingUtilities.invokeLater(() -> scrollToTop(content));
    }

    public static void styleInput(JComponent field) {
        field.setFont(AppTheme.regular(13));
        field.setForeground(AppTheme.foreground());
        field.setBackground(AppTheme.inputBackground());
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 40));
    }

    public static DocumentListener documentListener(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                action.run();
            }
        };
    }

    // ---- Centered dialogs -------------------------------------------------------
    // JOptionPane's convenience methods center on their parent component, which can
    // leave a confirmation off-centre when the window is wide. These wrappers always
    // place the dialog in the middle of the screen and keep the same return values as
    // the matching JOptionPane calls, so existing call sites need no other changes.

    /**
     * Confirmation popup. Every action button reads "Confirm" and every cancelling
     * button reads "Cancel", regardless of the JOptionPane option type used by the
     * caller. The returned value keeps the original JOptionPane constants so no
     * calling code has to change its decision logic. Closing the popup with X
     * returns CLOSED_OPTION, which all callers treat as "do nothing".
     */
    public static int showConfirmDialog(
            Component parent, Object message, String title, int optionType, int messageType) {
        Object[] options;
        if (optionType == JOptionPane.YES_NO_CANCEL_OPTION) {
            options = new Object[] { CONFIRM, CANCEL, CLOSE };
        } else if (optionType == JOptionPane.OK_CANCEL_OPTION
                || optionType == JOptionPane.YES_NO_OPTION) {
            options = new Object[] { CONFIRM, CANCEL };
        } else {
            options = new Object[] { OKAY };
        }
        JOptionPane pane = new JOptionPane(message, messageType,
                JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        Object value = showRawDialog(parent, title, pane);
        if (CONFIRM.equals(value) || OKAY.equals(value)) {
            return optionType == JOptionPane.OK_CANCEL_OPTION
                    ? JOptionPane.OK_OPTION : JOptionPane.YES_OPTION;
        }
        if (CANCEL.equals(value)) {
            return optionType == JOptionPane.OK_CANCEL_OPTION
                    ? JOptionPane.CANCEL_OPTION : JOptionPane.NO_OPTION;
        }
        if (CLOSE.equals(value)) {
            return JOptionPane.CLOSED_OPTION;
        }
        return value instanceof Integer ? (Integer) value : JOptionPane.CLOSED_OPTION;
    }

    /** Informational popup with a single "Okay" button. */
    public static void showMessageDialog(
            Component parent, Object message, String title, int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION,
                null, new Object[] { OKAY }, OKAY);
        showRawDialog(parent, title, pane);
    }

    public static String showInputDialog(
            Component parent, Object message, String title, int messageType) {
        return showInputDialog(parent, message, title, messageType, null);
    }

    /** Input popup that accepts an initial value (used when editing a room). */
    public static String showInputDialog(
            Component parent, Object message, String title, int messageType, String initialValue) {
        JTextField field = new JTextField(initialValue == null ? "" : initialValue, 22);
        Object[] prompt = { message, field };
        JOptionPane pane = new JOptionPane(prompt, messageType, JOptionPane.DEFAULT_OPTION,
                null, new Object[] { CONFIRM, CANCEL }, CONFIRM);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        Object value = showRawDialog(parent, title, pane);
        return confirmed(value) ? field.getText() : null;
    }

    /**
     * Generic centred option popup for dialogs with domain-specific labels, such as
     * the updater's "Update now" / "Later" choice. Returns the chosen option index.
     */
    public static int showOptionDialog(
            Component parent, Object message, String title, int messageType,
            Object[] options, Object initialValue) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION,
                null, options, initialValue);
        Object value = showRawDialog(parent, title, pane);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                return i;
            }
        }
        return JOptionPane.CLOSED_OPTION;
    }

    /** True only when the user chose the positive button (not Cancel or the X). */
    public static boolean confirmed(Object value) {
        return CONFIRM.equals(value) || OKAY.equals(value)
                || Integer.valueOf(JOptionPane.OK_OPTION).equals(value)
                || Integer.valueOf(JOptionPane.YES_OPTION).equals(value);
    }

    /**
     * Confirmation shown right after a recoverable delete. Returns 0 when Undo was
     * chosen, 1 when the user chose Close, and -1 if the dialog was closed with X.
     */
    public static int showUndoDialog(Component parent, String message, String title) {
        Object[] options = { "Undo", CLOSE };
        JOptionPane pane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        Object value = showRawDialog(parent, title, pane);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                return i;
            }
        }
        return JOptionPane.CLOSED_OPTION;
    }

    private static Object showRawDialog(Component parent, String title, JOptionPane pane) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null) {
            owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        }
        JDialog dialog;
        if (owner instanceof Dialog) {
            dialog = new JDialog((Dialog) owner, title, true);
        } else if (owner instanceof Frame) {
            dialog = new JDialog((Frame) owner, title, true);
        } else {
            dialog = new JDialog((Frame) null, title, true);
        }
        dialog.setContentPane(src.ui.components.Motion.popIn(pane));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, e -> {
            Object v = pane.getValue();
            if (v != null && v != JOptionPane.UNINITIALIZED_VALUE) {
                dialog.setVisible(false);
            }
        });
        dialog.pack();
        // Focus the default button/input so Enter activates Confirm and Escape can
        // dismiss the popup, just like a standard JOptionPane dialog.
        pane.selectInitialValue();
        dialog.setLocationRelativeTo(null); // centre of the screen, not the parent
        dialog.setVisible(true);
        Object value = pane.getValue();
        dialog.dispose();
        return value;
    }

    public static Color statusColor(String status) {
        if (status == null) {
            return AppTheme.muted();
        }
        switch (status) {
            case "RELEASED":
            case "PARTIALLY_RELEASED":
            case "FULLY_RELEASED":
                return AppTheme.primaryInk();
            case "ASSIGNED":
            case "APPROVED":
            case "PARTIALLY_APPROVED":
            case "FULLY_APPROVED":
            case "COMPLETED":
            case "RETURNED":
                return AppTheme.success();
            case "UNABLE":
            case "REJECTED":
            case "DECLINED":
            case "CANCELLED":
            case "NOT_ASSIGNED":
            case "NOT_PROCESSED":
                return AppTheme.danger();
            case "NEEDS_ACTION":
                return AppTheme.warning();
            default:
                return AppTheme.warning();
        }
    }

    public static String displayStatus(String status) {
        if ("RELEASED".equals(status)) {
            return "Borrowed";
        }
        if ("RECEIVED".equals(status)) {
            return "Received";
        }
        if ("PENDING_STAFF".equals(status)) {
            return "Staff review";
        }
        if ("PENDING_ADMIN".equals(status)) {
            return "Awaiting Facility Admin Approval";
        }
        if ("PENDING_DIRECTOR".equals(status)) {
            return "Awaiting Campus Director Approval";
        }
        if ("REJECTED".equals(status)) {
            return "Declined";
        }
        if ("NOT_ASSIGNED".equals(status)) {
            return "Not Assigned";
        }
        if ("NOT_PROCESSED".equals(status)) {
            return "Not Processed on Time";
        }
        if ("UNABLE".equals(status)) {
            return "Unable to fulfill";
        }
        if ("NEEDS_ACTION".equals(status)) {
            return "Needs action";
        }
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        String[] parts = status.toLowerCase().split("_");
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return text.toString();
    }

    /** Label for one department's own status (ASSIGNED reads as "Approved"). */
    public static String departmentStatus(String status) {
        if (status == null) {
            return "Not involved";
        }
        switch (status) {
            case "PENDING":
                return "Pending";
            case "ASSIGNED":
                return "Accepted (Initial review)";
            case "RELEASED":
                return "Released";
            case "RECEIVED":
                return "Received";
            case "RETURNED":
                return "Returned";
            default:
                return displayStatus(status);
        }
    }

    public static java.awt.Color departmentStatusColor(String status) {
        if ("ASSIGNED".equals(status)) {
            return AppTheme.primaryInk(); // not green; distinct from final approval
        }
        if ("RELEASED".equals(status)) {
            return AppTheme.primaryInk();
        }
        return statusColor(status);
    }

    /**
     * Row of badges: overall status, review stage when relevant, and each
     * department's own decision (unless the view is limited to one department).
     */
    public static javax.swing.JPanel statusBadges(
            src.model.BorrowRequest request, String scopeRole, boolean showDepartments) {
        javax.swing.JPanel badges = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        badges.setOpaque(false);
        if (request.isPriority()) {
            badges.add(new src.ui.components.StatusBadge(
                    "Faculty · Priority", AppTheme.accent()));
        }
        if (showDepartments) {
            for (String role : new String[] { src.model.RequestStatus.ICT,
                    src.model.RequestStatus.FACILITY }) {
                if (scopeRole != null && !scopeRole.equalsIgnoreCase(role)) {
                    continue;
                }
                String status = src.model.RequestStatus.department(request, role);
                if (status == null) {
                    continue;
                }
                badges.add(new src.ui.components.StatusBadge(
                        src.model.RequestStatus.departmentLabel(role) + ": "
                                + departmentStatus(status),
                        departmentStatusColor(status)));
            }
        }
        String stage = request.getStatus();
        if ("PENDING_ADMIN".equals(stage)) {
            badges.add(new src.ui.components.StatusBadge(
                    "Awaiting Facility Admin Approval", AppTheme.warning()));
        } else if ("PENDING_DIRECTOR".equals(stage)) {
            badges.add(new src.ui.components.StatusBadge(
                    "Awaiting Campus Director Approval", AppTheme.warning()));
        }
        badges.add(new src.ui.components.StatusBadge(request.getOverallStatus(scopeRole)));
        return badges;
    }

    /** Borrower classification stored with the request (Faculty / Student). */
    public static String classification(src.model.BorrowRequest request) {
        String type = request == null ? null : request.getBorrowerType();
        if (type == null || type.isBlank()) {
            return "—";
        }
        if ("FACULTY".equalsIgnoreCase(type)) return "Faculty";
        if ("STUDENT".equalsIgnoreCase(type)) return "Student";
        return type;
    }

    /** "09:00 AM – 10:00 AM" style time range. */
    public static String timeRange(java.time.LocalTime start, java.time.LocalTime end) {
        java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter
                .ofPattern("hh:mm a", java.util.Locale.ENGLISH);
        return start.format(format) + " – " + end.format(format);
    }

    /** Keeps page content at its preferred height and tracks the viewport width. */
    private static final class TopAlignedView extends javax.swing.JPanel
            implements javax.swing.Scrollable {

        TopAlignedView(JComponent content) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            add(content, java.awt.BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
                java.awt.Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(
                java.awt.Rectangle visible, int orientation, int direction) {
            return visible.height - 36;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getPreferredSize().height < getParent().getHeight();
        }
    }
}
