package src.service;

import src.controller.AuthController;
import src.controller.NotificationController;
import src.model.Notification;
import src.model.User;
import src.util.NotificationSound;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Watches for genuinely new notifications independently of any visible panel.
 *
 * <p>The baseline is set on the first successful poll after login, so opening the
 * dashboard, changing pages, restoring a hidden window, or reconnecting never
 * replays old notifications as new. All sound playback is centralized here so the
 * UI poll cannot play the same notification twice.</p>
 */
public final class BackgroundNotificationService implements AutoCloseable {

    private static final long POLL_SECONDS = 5;
    private static final long SOUND_GAP_MS = 2500L;
    private static final long DETAIL_REFRESH_MS = 60_000L;

    private final AuthController authController;
    private final NotificationController notificationController;
    private final Supplier<User> userSupplier;
    private final String sessionToken;
    private final Runnable onSessionInvalid;
    private final ScheduledExecutorService executor;

    private volatile long lastNotificationId = -1L;
    private volatile boolean primed;
    private volatile boolean suppressNextSound;
    private volatile long lastDetailRefresh;

    public BackgroundNotificationService(
            AuthController authController,
            NotificationController notificationController,
            Supplier<User> userSupplier,
            String sessionToken,
            Runnable onSessionInvalid) {
        this.authController = authController;
        this.notificationController = notificationController;
        this.userSupplier = userSupplier;
        this.sessionToken = sessionToken;
        this.onSessionInvalid = onSessionInvalid;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "equiplink-notification-monitor");
            // A non-daemon thread keeps background notifications alive after the
            // dashboard window is hidden.
            thread.setDaemon(false);
            return thread;
        });
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::pollSafely, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    public void connectionChanged() {
        suppressNextSound = true;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void pollSafely() {
        try {
            poll();
        } catch (Exception exception) {
            // A failed poll may have skipped notifications created while the server
            // was unavailable. Do not turn that gap into a burst of sounds on reconnect.
            suppressNextSound = true;
        }
    }

    private void poll() throws Exception {
        if (!sessionTokenMatchesUser()) {
            return;
        }
        if (!src.service.SessionService.isValid(sessionToken)) {
            stopAndReturnToLogin();
            return;
        }

        long latest = notificationController.latestNotificationId(userSupplier.get().getUserId());
        if (!primed) {
            lastNotificationId = latest;
            primed = true;
            suppressNextSound = false;
            lastDetailRefresh = System.currentTimeMillis();
        } else if (latest > lastNotificationId) {
            lastNotificationId = latest;
            if (suppressNextSound) {
                suppressNextSound = false;
            } else {
                playSoundForLatest();
            }
        }

        // findForUser also generates due reminders/overdue notices; refreshing it here
        // lets those arrive while the dashboard is hidden.
        if (System.currentTimeMillis() - lastDetailRefresh > DETAIL_REFRESH_MS) {
            notificationController.findForUser(userSupplier.get().getUserId());
            lastDetailRefresh = System.currentTimeMillis();
            long refreshed = notificationController.latestNotificationId(
                    userSupplier.get().getUserId());
            if (refreshed > lastNotificationId) {
                lastNotificationId = refreshed;
            }
        }
    }

    private boolean sessionTokenMatchesUser() {
        return sessionToken != null && userSupplier.get() != null;
    }

    private void playSoundForLatest() {
        User user = userSupplier.get();
        if (user == null || !user.isNotificationsEnabled()
                || !user.isNotificationSoundEnabled()) {
            return;
        }
        NotificationSound.play(user.getNotificationSound(), SOUND_GAP_MS);
    }

    private void stopAndReturnToLogin() {
        close();
        SwingUtilities.invokeLater(() -> {
            javax.swing.JOptionPane.showMessageDialog(
                    null,
                    "Your saved session is no longer valid. Please sign in again.",
                    "Session ended",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            onSessionInvalid.run();
        });
    }

    /** Uses the controller only to keep session checks in the application layer. */
    private boolean authReady() {
        return authController != null;
    }
}
