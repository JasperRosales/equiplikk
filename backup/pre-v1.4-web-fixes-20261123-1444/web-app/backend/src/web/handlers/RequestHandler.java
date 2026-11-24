package web.handlers;

import src.controller.BorrowRequestController;
import src.model.BorrowRequest;
import src.model.User;
import web.main.AppServices;
import web.service.DashboardView;
import web.service.RequestExportService;
import web.service.WebForms;
import web.service.WebHistoryRepository;
import web.webapi.ApiContext;

import java.util.List;
import java.util.Map;

/** All request workflows. Every operation reuses BorrowRequestController unchanged. */
public final class RequestHandler {

    private RequestHandler() {
    }

    public static void handle(ApiContext context, AppServices services) throws Exception {
        if ("export".equals(context.segment(0))) {
            export(context, services);
            return;
        }
        if (!"requests".equals(context.segment(0))) {
            context.error(404, "Unknown request endpoint.");
            return;
        }

        String method = context.method();
        String action = context.segment(1);

        if ("dashboard".equals(action) && method.equals("GET")) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            context.ok(DashboardView.build(actor, services.requests,
                    services.equipment, services.notifications));
            return;
        }

        if (method.equals("POST") && action.isBlank()) {
            User actor = context.require("BORROWER");
            if (actor == null) return;
            long id = services.requests.createRequest(actor.getUserId(),
                    WebForms.requestForm(context.body()));
            context.created(Map.of("requestId", id, "message", "Request submitted."));
            return;
        }

        if ("admin".equals(action)) {
            User actor = context.require("ICT_STAFF");
            if (actor == null) return;
            long id = context.longSegment(2);
            if ("delete".equals(context.segment(3)) && method.equals("DELETE")) {
                services.requests.deleteRequestAsAdmin(actor, id, context.text("reason"));
                context.ok(Map.of("message", "Request removed. It can be restored briefly."));
                return;
            }
            if ("restore".equals(context.segment(3)) && method.equals("POST")) {
                services.requests.restoreRequestAsAdmin(actor, id);
                context.ok(Map.of("message", "Request restored."));
                return;
            }
            context.error(404, "Unknown admin action.");
            return;
        }

        if (method.equals("GET")) {
            list(context, services, action);
            return;
        }

        long requestId;
        try {
            requestId = Long.parseLong(action);
        } catch (NumberFormatException exception) {
            context.error(404, "Unknown request endpoint.");
            return;
        }

        if (method.equals("PUT")) {
            User actor = context.require("BORROWER");
            if (actor == null) return;
            services.requests.updateRequest(actor.getUserId(), requestId,
                    WebForms.requestForm(context.body()));
            context.ok(Map.of("message", "Request updated. Staff review starts again."));
            return;
        }

        if (method.equals("DELETE")) {
            User actor = context.require("BORROWER");
            if (actor == null) return;
            services.requests.deleteRequest(actor.getUserId(), requestId);
            context.noContent();
            return;
        }

        if (!method.equals("POST")) {
            context.error(404, "Unknown request endpoint.");
            return;
        }

        String operation = context.segment(2);
        if ("restore".equals(operation)) {
            User actor = context.require("BORROWER");
            if (actor == null) return;
            services.requests.restoreRequest(actor.getUserId(), requestId);
            context.ok(Map.of("message", "Request restored."));
            return;
        }

        User actor = context.require(rolesFor(operation));
        if (actor == null) return;

        switch (operation == null ? "" : operation) {
            case "receive" -> {
                services.requests.receiveByBorrower(actor, requestId);
                context.ok(Map.of("message", "Receipt confirmed."));
            }
            case "assign" -> {
                List<Object> assignments = context.list("assignments");
                services.requests.assignStaffRequest(actor, requestId,
                        assignments.isEmpty() ? null : WebForms.assignments(assignments));
                context.ok(Map.of("message", "Equipment assignment saved."));
            }
            case "decline" -> {
                services.requests.declineStaffRequest(actor, requestId, context.text("remarks"));
                context.ok(Map.of("message", "Department request declined."));
            }
            case "needs-action" -> {
                services.requests.markStaffRequestNeedsAction(
                        actor, requestId, context.text("remarks"));
                context.ok(Map.of("message", "Borrower asked to update the request."));
            }
            case "release" -> {
                long group = context.number("groupId");
                List<Object> conditions = context.list("conditions");
                services.requests.releaseStaffAssignment(actor, requestId,
                        group > 0 ? group : null, WebForms.releases(conditions));
                context.ok(Map.of("message", "Equipment released."));
            }
            case "return" -> {
                services.requests.recordStaffReturn(actor, requestId,
                        WebForms.returns(context.list("inspections")));
                context.ok(Map.of("message", "Return inspection recorded."));
            }
            case "admin-review" -> {
                services.requests.reviewByAdmin(actor, requestId,
                        context.flag("approve"), context.text("remarks"));
                context.ok(Map.of("message", "Admin review saved."));
            }
            case "director-review" -> {
                services.requests.reviewByDirector(actor, requestId,
                        context.flag("approve"), context.text("remarks"));
                context.ok(Map.of("message", "Final decision saved."));
            }
            default -> context.error(404, "Unknown request action.");
        }
    }

    private static String[] rolesFor(String operation) {
        if (operation == null) return ApiContext.ALL_ROLES();
        return switch (operation) {
            case "receive" -> new String[] { "BORROWER" };
            case "assign", "decline", "needs-action", "release", "return" -> ApiContext.STAFF();
            case "admin-review" -> new String[] { "ADMIN" };
            case "director-review" -> new String[] { "DIRECTOR" };
            default -> ApiContext.ALL_ROLES();
        };
    }

    private static void list(ApiContext context, AppServices services, String action)
            throws Exception {
        BorrowRequestController controller = services.requests;
        switch (action == null ? "" : action) {
            case "active" -> {
                User actor = context.require("BORROWER");
                if (actor == null) return;
                context.ok(controller.findActiveRequests(actor.getUserId()));
            }
            case "history" -> {
                User actor = context.require("BORROWER");
                if (actor == null) return;
                context.ok(controller.findBorrowingHistory(actor.getUserId()));
            }
            case "all" -> {
                User actor = context.require(ApiContext.ALL_ROLES());
                if (actor == null) return;
                context.ok("BORROWER".equalsIgnoreCase(actor.getRole())
                        ? controller.findAllForBorrower(actor.getUserId())
                        : controller.findAllRequests(actor));
            }
            case "queue" -> {
                User actor = context.require(ApiContext.STAFF_REVIEWERS());
                if (actor == null) return;
                String role = actor.getRole();
                if ("ADMIN".equalsIgnoreCase(role)) {
                    context.ok(controller.findAdminQueue(actor));
                } else if ("DIRECTOR".equalsIgnoreCase(role)) {
                    context.ok(controller.findDirectorQueue(actor));
                } else {
                    context.ok(controller.findStaffQueue(actor));
                }
            }
            case "schedule" -> {
                User actor = context.require(ApiContext.STAFF());
                if (actor == null) return;
                context.ok(controller.findScheduleForRole(actor));
            }
            case "transactions" -> {
                User actor = context.require(ApiContext.STAFF_REVIEWERS());
                if (actor == null) return;
                context.ok("ICT_STAFF".equalsIgnoreCase(actor.getRole())
                                || "FACILITY_STAFF".equalsIgnoreCase(actor.getRole())
                        ? controller.findHistoryForRole(actor)
                        : controller.findAllRequests(actor));
            }
            case "room-conflicts" -> {
                User actor = context.require(ApiContext.STAFF_REVIEWERS());
                if (actor == null) return;
                context.ok(controller.findRoomConflicts(actor));
            }
            default -> {
                if (action == null || action.isBlank()) {
                    context.error(404, "A request id or list name is required.");
                    return;
                }
                long requestId;
                try {
                    requestId = Long.parseLong(action);
                } catch (NumberFormatException exception) {
                    context.error(404, "Unknown request list.");
                    return;
                }
                detail(context, services, requestId);
            }
        }
    }

    private static void detail(ApiContext context, AppServices services, long requestId)
            throws Exception {
        User actor = context.require(ApiContext.ALL_ROLES());
        if (actor == null) return;
        String sub = context.segment(2);
        BorrowRequest request = services.requests.findRequestForUser(actor, requestId);
        if (request == null) {
            context.error(404, "Request not found.");
            return;
        }
        if ("history".equals(sub)) {
            context.ok(WebHistoryRepository.forRequest(requestId));
            return;
        }
        if ("availability".equals(sub)) {
            if (!"BORROWER".equalsIgnoreCase(actor.getRole())) {
                String scope = ApiContext.oneOf(actor.getRole(),
                        "ICT_STAFF", "FACILITY_STAFF") ? actor.getRole() : "";
                context.ok(services.requests.findAvailabilityNotes(actor, requestId, scope));
                return;
            }
            context.ok(List.of());
            return;
        }
        if ("assignment-availability".equals(sub)) {
            context.require(ApiContext.STAFF());
            if (context.user() == null) return;
            context.ok(services.requests.availableAssignmentQuantities(actor, requestId));
            return;
        }
        context.ok(request);
    }

    private static void export(ApiContext context, AppServices services) throws Exception {
        User actor = context.require(ApiContext.STAFF_REVIEWERS());
        if (actor == null) return;
        if (!"request-form".equals(context.segment(1))) {
            context.error(404, "Unknown export type.");
            return;
        }
        long requestId = context.longSegment(2);
        BorrowRequest request = services.requests.findRequestForUser(actor, requestId);
        services.requests.verifyRequestFormExport(actor, request);
        byte[] html = RequestExportService.html(request);
        context.bytes(200, html, "text/html; charset=utf-8",
                request.getRequestCode() + ".html");
    }
}
