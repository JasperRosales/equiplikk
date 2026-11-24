package web.handlers;

import src.model.User;
import web.main.AppServices;
import web.webapi.ApiContext;

import java.util.List;
import java.util.Map;

/** Notification list and actions; delegates sweep/read behavior to NotificationController. */
public final class NotificationHandler {

    private NotificationHandler() {
    }

    public static void handle(ApiContext context, AppServices services) throws Exception {
        User actor = context.require(ApiContext.ALL_ROLES());
        if (actor == null) return;
        String method = context.method();
        String action = context.segment(1);

        if (method.equals("GET") && action.isBlank()) {
            context.ok(services.notifications.findForUser(actor.getUserId()));
            return;
        }
        if (method.equals("GET") && "unread-count".equals(action)) {
            context.ok(Map.of("count", services.notifications.countUnread(actor.getUserId())));
            return;
        }
        if (method.equals("POST") && "read-all".equals(action)) {
            services.notifications.markAllRead(actor.getUserId());
            context.ok(Map.of("message", "All notifications marked as read."));
            return;
        }
        if (method.equals("POST") && "delete-all".equals(action)) {
            context.ok(Map.of("deleted", services.notifications.deleteAll(actor.getUserId())));
            return;
        }
        if (method.equals("POST") && "restore-all".equals(action)) {
            List<Long> ids = context.list("ids").stream()
                    .map(value -> Long.parseLong(String.valueOf(value)))
                    .toList();
            context.ok(Map.of("restored", services.notifications.restoreAll(actor.getUserId(), ids)));
            return;
        }
        if (method.equals("POST") && "sweep".equals(action)) {
            services.notifications.runOverdueSweep();
            context.ok(Map.of("message", "Overdue check completed."));
            return;
        }
        if (method.equals("POST") && "reminder".equals(action)) {
            int pending = context.integer("pendingCount");
            if (!services.notifications.hasPendingReminderToday(actor.getUserId())) {
                services.notifications.createPendingReminder(actor.getUserId(), Math.max(0, pending));
            }
            context.ok(Map.of("message", "Reminder checked."));
            return;
        }

        long notificationId;
        try {
            notificationId = Long.parseLong(action);
        } catch (NumberFormatException exception) {
            context.error(404, "Unknown notification endpoint.");
            return;
        }

        if (method.equals("POST") && "read".equals(context.segment(2))) {
            services.notifications.markRead(actor.getUserId(), notificationId);
            context.ok(Map.of("message", "Notification marked as read."));
            return;
        }
        if (method.equals("POST") && "restore".equals(context.segment(2))) {
            services.notifications.restore(actor.getUserId(), notificationId);
            context.ok(Map.of("message", "Notification restored."));
            return;
        }
        if (method.equals("DELETE")) {
            services.notifications.delete(actor.getUserId(), notificationId);
            context.noContent();
            return;
        }
        context.error(404, "Unknown notification endpoint.");
    }
}
