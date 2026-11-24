package web.handlers;

import src.model.BorrowRequest;
import src.model.User;
import web.main.AppServices;
import web.webapi.ApiContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** User management, account details and ICT password request review. */
public final class UserHandler {

    private UserHandler() {
    }

    public static void handleUsers(ApiContext context, AppServices services) throws Exception {
        String method = context.method();
        String action = context.segment(1);

        if ("borrowers".equals(action) && method.equals("GET")) {
            User actor = context.require("ICT_STAFF");
            if (actor == null) return;
            context.ok(services.auth.findBorrowersForIct(actor));
            return;
        }

        if ("faculty".equals(action) && method.equals("POST")) {
            User actor = context.require("ICT_STAFF");
            if (actor == null) return;
            services.auth.createFacultyAccount(actor,
                    context.text("username"), context.text("email"),
                    context.text("fullName"), context.text("department"),
                    context.text("password").toCharArray());
            context.created(Map.of("message", "Faculty account created."));
            return;
        }

        long userId;
        try {
            userId = Long.parseLong(action);
        } catch (NumberFormatException exception) {
            context.error(404, "Unknown user endpoint.");
            return;
        }

        User actor = context.require("ICT_STAFF");
        if (actor == null) return;

        if ("active".equals(context.segment(2)) && method.equals("POST")) {
            services.auth.setBorrowerActive(actor, userId, context.flag("active"));
            context.ok(Map.of("message", "Account status updated."));
            return;
        }
        if ("restore".equals(context.segment(2)) && method.equals("POST")) {
            services.auth.restoreBorrowerAccount(actor, userId);
            context.ok(Map.of("message", "Account restored."));
            return;
        }
        if ("account".equals(context.segment(2)) && method.equals("GET")) {
            List<BorrowRequest> requests = services.requests.findAllForAccount(actor, userId);
            List<Long> ids = new ArrayList<>();
            for (BorrowRequest request : requests) {
                ids.add(request.getRequestId());
            }
            context.ok(Map.of(
                    "requests", requests,
                    "history", services.requests.findHistoryForAccount(actor, userId, ids)));
            return;
        }
        if (method.equals("DELETE")) {
            services.auth.deleteBorrowerAccount(actor, userId);
            context.ok(Map.of("message", "Account removed. Its history is preserved."));
            return;
        }
        context.error(404, "Unknown user endpoint.");
    }

    public static void handlePasswordRequests(ApiContext context, AppServices services)
            throws Exception {
        User actor = context.require("ICT_STAFF");
        if (actor == null) return;
        String method = context.method();
        String action = context.segment(1);

        if (method.equals("GET") && action.isBlank()) {
            context.ok(services.auth.findPasswordResetRequests(actor));
            return;
        }
        long requestId;
        try {
            requestId = Long.parseLong(action);
        } catch (NumberFormatException exception) {
            context.error(404, "Unknown password request endpoint.");
            return;
        }
        if (method.equals("POST") && "review".equals(context.segment(2))) {
            services.auth.reviewPasswordReset(actor, requestId,
                    context.flag("approve"), context.text("note"));
            context.ok(Map.of("message", "Password request reviewed."));
            return;
        }
        context.error(404, "Unknown password request endpoint.");
    }
}
