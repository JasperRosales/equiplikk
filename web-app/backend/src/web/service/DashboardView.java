package web.service;

import src.controller.BorrowRequestController;
import src.controller.EquipmentController;
import src.controller.NotificationController;
import src.model.BorrowRequest;
import src.model.RequestStatus;
import src.model.User;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Same counters and preview lists as the desktop dashboard, assembled from existing controllers. */
public final class DashboardView {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private DashboardView() {
    }

    public static Map<String, Object> build(User user,
            BorrowRequestController requests,
            EquipmentController equipment,
            NotificationController notifications) throws Exception {

        LocalDate today = LocalDate.now(MANILA);
        List<BorrowRequest> activity = requests.findDailyActivity(user, today);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> cards = new ArrayList<>();

        String role = user.getRole() == null ? "" : user.getRole().toUpperCase();
        boolean borrower = "BORROWER".equals(role);
        boolean staff = "ICT_STAFF".equals(role) || "FACILITY_STAFF".equals(role);

        if (borrower) {
            int active = 0;
            for (BorrowRequest request : activity) {
                String status = request.getStatus();
                if (!"COMPLETED".equals(status) && !"REJECTED".equals(status)
                        && !"CANCELLED".equals(status)) {
                    active++;
                }
            }
            card(cards, "Active requests", active, "requests", "my-requests");
            card(cards, "Pending", count(RequestStatus.Category.PENDING, activity, null),
                    "requests", "my-requests?status=PENDING");
            card(cards, "Approved", count(RequestStatus.Category.APPROVED, activity, null),
                    "requests", "my-requests?status=APPROVED");
            int released = count(RequestStatus.Category.PARTIALLY_RELEASED, activity, null)
                    + count(RequestStatus.Category.FULLY_RELEASED, activity, null);
            card(cards, "Released", released, "requests", "my-requests?status=RELEASED");
        } else if (staff) {
            card(cards, "Total units", equipment.countTotalUnitsForRole(role), "units", "inventory");
            card(cards, "Available", equipment.countAvailableUnitsForRole(role), "units", "inventory");
            card(cards, "Reserved today", equipment.countReservedUnitsToday(role), "units", "schedule");
            card(cards, "In use", equipment.countInUseUnitsToday(role), "units", "inventory");
            card(cards, "Pending", count(RequestStatus.Category.PENDING, activity, role),
                    "requests", "requests");
            card(cards, "Approved", count(RequestStatus.Category.APPROVED, activity, role),
                    "requests", "requests");
            card(cards, "Declined", count(RequestStatus.Category.DECLINED, activity, role),
                    "requests", "transactions");
            card(cards, "Overdue", count(RequestStatus.Category.OVERDUE, activity, role),
                    "requests", "schedule");
        } else {
            boolean director = "DIRECTOR".equals(role);
            RequestStatus.Category review = director
                    ? RequestStatus.Category.DIRECTOR_REVIEW
                    : RequestStatus.Category.ADMIN_REVIEW;
            card(cards, "Awaiting approval", count(review, activity, null), "requests",
                    director ? "approval" : "all-requests?status=PENDING_ADMIN");
            // Daily Approved Summary: one per request, by actual final-approval timestamp.
            card(cards, "Approved today", requests.countFullyApprovedOn(user, today),
                    "requests", director ? "transactions" : "all-requests?approvedToday=1");
            card(cards, "Declined", count(RequestStatus.Category.DECLINED, activity, null),
                    "requests", director ? "transactions" : "all-requests?status=DECLINED");
            card(cards, "Overdue", count(RequestStatus.Category.OVERDUE, activity, null),
                    "requests", director ? "transactions"
                            : "all-requests?statuses=NOT_ASSIGNED,NOT_PROCESSED");
        }

        List<BorrowRequest> preview;
        if (borrower) {
            preview = requests.findActiveRequests(user.getUserId());
        } else if (staff) {
            preview = requests.findStaffQueue(user);
        } else if ("DIRECTOR".equals(role)) {
            preview = requests.findDirectorQueue(user);
        } else {
            preview = requests.findAdminQueue(user);
        }
        int limit = "DIRECTOR".equals(role) ? preview.size() : Math.min(3, preview.size());

        result.put("role", role);
        result.put("displayRole", displayRole(role));
        result.put("cards", cards);
        result.put("recent", preview.subList(0, limit));
        result.put("unreadNotifications", notifications.countUnread(user.getUserId()));
        result.put("queueCount", requests.countQueueForRole(role));
        return result;
    }

    private static int count(RequestStatus.Category category, List<BorrowRequest> requests,
            String scope) {
        return (int) category.count(requests, scope);
    }

    private static void card(List<Map<String, Object>> cards, String label, int value,
            String unit, String target) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("label", label);
        card.put("value", value);
        card.put("unit", unit);
        card.put("target", target);
        cards.add(card);
    }

    private static String displayRole(String role) {
        return switch (role) {
            case "ICT_STAFF" -> "System Admin";
            case "FACILITY_STAFF" -> "Facility Staff";
            case "ADMIN" -> "Facility Admin";
            case "DIRECTOR" -> "Campus Director";
            case "BORROWER" -> "Borrower";
            default -> role;
        };
    }
}
