package web.handlers;

import src.model.User;
import web.main.AppServices;
import web.webapi.ApiContext;

import java.util.Map;

/** Login, registration, logout and password flows — all delegated to AuthController. */
public final class AuthHandler {

    private AuthHandler() {
    }

    public static void handle(ApiContext context, AppServices services) throws Exception {
        String route = context.segment(1);
        String method = context.method();

        if ("login".equals(route) && method.equals("POST")) {
            User user = services.auth.login(context.text("username"),
                    context.text("password").toCharArray());
            if (user == null) {
                context.error(401, "Invalid username or password.");
                return;
            }
            var session = services.sessions.create(user);
            context.setCookie(services.sessions.cookie(session));
            context.ok(Map.of("user", user, "token", session.token()));
            return;
        }

        if ("register".equals(route) && method.equals("POST")) {
            services.auth.registerBorrower(
                    context.text("username"), context.text("email"),
                    context.text("fullName"), context.text("department"),
                    context.text("borrowerType").isBlank()
                            ? "STUDENT" : context.text("borrowerType"),
                    context.text("password").toCharArray());
            context.created(Map.of("message", "Account created. You can sign in now."));
            return;
        }

        if ("logout".equals(route) && method.equals("POST")) {
            if (context.session() != null) {
                services.sessions.invalidate(context.session().token());
            }
            context.setCookie(services.sessions.clearingCookie());
            context.noContent();
            return;
        }

        if ("me".equals(route) && method.equals("GET")) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            context.ok(Map.of("user", actor));
            return;
        }

        if ("password".equals(route) && method.equals("POST")) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            services.auth.changePassword(actor.getUserId(),
                    context.text("currentPassword").toCharArray(),
                    context.text("newPassword").toCharArray());
            context.ok(Map.of("message", "Password changed."));
            return;
        }

        if ("password-request".equals(route) && method.equals("POST")) {
            services.auth.requestPasswordReset(
                    context.text("usernameOrEmail"), context.text("registeredEmail"));
            context.ok(Map.of("message",
                    "Your request was submitted. An ICT staff member will review it."));
            return;
        }

        if ("password-request".equals(route) && "complete".equals(context.segment(2))
                && method.equals("POST")) {
            services.auth.resetPasswordAfterApproval(
                    context.text("usernameOrEmail"), context.text("registeredEmail"),
                    context.text("newPassword").toCharArray());
            context.ok(Map.of("message", "Password reset. You can sign in now."));
            return;
        }

        context.error(404, "Unknown authentication endpoint.");
    }
}
