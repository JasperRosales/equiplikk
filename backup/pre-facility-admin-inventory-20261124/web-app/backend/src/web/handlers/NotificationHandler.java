package web.handlers;

import src.model.BorrowRequest;
import src.model.EquipmentRequestItem;
import src.model.User;
import web.main.AppServices;
import web.webapi.ApiContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            context.ok(withRequestDetails(actor, services.notifications.findForUser(actor.getUserId()), services));
            return;
        }
        if (method.equals("GET") && "unread-count".equals(action)) {
            context.ok(Map.of("count", services.notifications.countUnread(actor.getUserId())));
            return;
        }
        if (method.equals("GET") && "latest-id".equals(action)) {
            context.ok(Map.of("id", services.notifications.latestNotificationId(actor.getUserId())));
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

    /**
     * Adds a read-only projection of the linked request so the browser can show the real
     * borrower, equipment names, quantities and status. Permission checks stay in the
     * existing BorrowRequestController; a notification whose request is no longer visible
     * keeps only its own stored fields.
     */
    private static List<Map<String, Object>> withRequestDetails(
            User actor, List<src.model.Notification> notifications, AppServices services) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (src.model.Notification notification : notifications) {
            // Json.object() only accepts Map values, so beans must be projected explicitly.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("notificationId", notification.getNotificationId());
            row.put("userId", notification.getUserId());
            row.put("requestId", notification.getRequestId());
            row.put("title", notification.getTitle());
            row.put("message", notification.getMessage());
            row.put("read", notification.isRead());
            row.put("createdAt", notification.getCreatedAt() == null
                    ? null : notification.getCreatedAt().toString());
            row.put("requestEquipment", notification.getRequestEquipment());
            row.put("requestCategories", notification.getRequestCategories());
            row.put("eventDate", notification.getEventDate() == null
                    ? null : notification.getEventDate().toString());
            row.put("category", notification.getCategory());
            row.put("categoryLabel", notification.getCategoryLabel());
            row.put("requestCode", notification.getRequestCode());
            row.put("department", notification.getDepartment());
            row.put("action", notification.getAction());
            row.put("requestStatus", notification.getRequestStatus());

            Long requestId = notification.getRequestId();
            if (requestId != null) {
                try {
                    BorrowRequest request = services.requests.findRequestForUser(actor, requestId);
                    row.put("borrowerName", request.getBorrowerName());
                    row.put("eventDate", request.getEventDate() == null
                            ? null : request.getEventDate().toString());
                    row.put("requestStatus", request.getStatus());
                    row.put("startTime", request.getStartTime() == null
                            ? null : request.getStartTime().toString());
                    row.put("endTime", request.getEndTime() == null
                            ? null : request.getEndTime().toString());
                    if (request.getRequestCode() != null && !request.getRequestCode().isBlank()) {
                        row.put("requestCode", request.getRequestCode());
                    }
                    List<Map<String, Object>> items = new ArrayList<>();
                    if (request.getEquipmentItems() != null) {
                        for (EquipmentRequestItem item : request.getEquipmentItems()) {
                            Map<String, Object> detail = new LinkedHashMap<>();
                            detail.put("equipmentId", item.getEquipmentId());
                            detail.put("equipmentCode", item.getEquipmentCode());
                            detail.put("equipmentName", item.getEquipmentName());
                            detail.put("category", item.getCategory());
                            detail.put("requestedQuantity", item.getRequestedQuantity());
                            detail.put("assignedQuantity", item.getAssignedQuantity());
                            items.add(detail);
                        }
                    }
                    row.put("equipmentItems", items);
                } catch (Exception ignored) {
                    // The notification itself remains valid even if the request was removed.
                }
            }
            rows.add(row);
        }
        return rows;
    }
}
