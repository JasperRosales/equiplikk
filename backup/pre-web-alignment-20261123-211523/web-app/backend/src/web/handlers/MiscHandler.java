package web.handlers;

import src.model.User;
import web.main.AppServices;
import web.service.DashboardView;
import web.webapi.ApiContext;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dashboard, shared venue list, profile/settings and small metadata endpoints. */
public final class MiscHandler {

    private MiscHandler() {
    }

    public static void handleDashboard(ApiContext context, AppServices services) throws Exception {
        User actor = context.require(ApiContext.ALL_ROLES());
        if (actor == null) return;
        context.ok(DashboardView.build(actor, services.requests,
                services.equipment, services.notifications));
    }

    public static void handleVenues(ApiContext context, AppServices services) throws Exception {
        if (context.method().equals("GET")) {
            context.require(ApiContext.ALL_ROLES());
            if (context.user() == null) return;
            List<String> venues = services.venues.findAll();
            if (venues.isEmpty()) {
                venues = services.venues.bundled();
            }
            context.ok(Map.of("venues", venues));
            return;
        }
        User actor = context.require("ICT_STAFF");
        if (actor == null) return;
        if (!context.method().equals("PUT")) {
            context.error(404, "Unknown venue endpoint.");
            return;
        }
        List<String> rooms = new ArrayList<>();
        for (Object value : context.list("venues")) {
            String room = String.valueOf(value).trim();
            if (!room.isBlank()) {
                rooms.add(room);
            }
        }
        services.venues.saveAll(rooms);
        context.ok(Map.of("message", "Venue list saved. Desktop and web now share it.",
                "venues", rooms));
    }

    public static void handleProfile(ApiContext context, AppServices services) throws Exception {
        String method = context.method();
        String action = context.segment(1);

        if ("picture".equals(action) && method.equals("GET")) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            byte[] picture = actor.getProfilePicture();
            if (picture == null || picture.length == 0) {
                context.error(404, "No profile picture has been uploaded.");
                return;
            }
            context.bytes(200, picture, "image/png", null);
            return;
        }

        User actor = context.require(ApiContext.ALL_ROLES());
        if (actor == null) return;

        if (action.isBlank() && method.equals("GET")) {
            context.ok(Map.of("user", actor));
            return;
        }
        if (action.isBlank() && method.equals("PUT")) {
            String encoded = context.text("profilePictureBase64");
            byte[] picture = encoded.isBlank()
                    ? actor.getProfilePicture() : decodePicture(encoded);
            services.auth.updateOwnProfile(actor, context.text("username"),
                    context.text("fullName"), context.text("department"), picture);
            context.ok(Map.of("message", "Profile saved."));
            return;
        }
        if ("notifications".equals(action) && method.equals("PUT")) {
            services.auth.updateNotificationPreference(actor.getUserId(), context.flag("enabled"));
            context.ok(Map.of("message", "Notification preference saved."));
            return;
        }
        if ("sound".equals(action) && method.equals("PUT")) {
            services.auth.updateNotificationSoundPreference(actor,
                    context.flag("enabled"), context.text("sound"));
            context.ok(Map.of("message", "Notification sound saved."));
            return;
        }
        context.error(404, "Unknown profile endpoint.");
    }

    public static void handleMeta(ApiContext context, AppServices services) throws Exception {
        context.require(ApiContext.ALL_ROLES());
        if (context.user() == null) return;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("roles", List.of("BORROWER", "ICT_STAFF", "FACILITY_STAFF",
                "ADMIN", "DIRECTOR"));
        meta.put("labels", Map.of(
                "BORROWER", "Borrower",
                "ICT_STAFF", "System Admin",
                "FACILITY_STAFF", "Facility Staff",
                "ADMIN", "Facility Admin",
                "DIRECTOR", "Campus Director"));
        meta.put("statuses", List.of("PENDING", "PENDING_ADMIN", "PENDING_DIRECTOR",
                "APPROVED", "RELEASED", "COMPLETED", "DECLINED", "NEEDS_ACTION"));
        context.ok(meta);
    }

    private static byte[] decodePicture(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String encoded = value;
        int comma = encoded.indexOf(',');
        if (encoded.startsWith("data:") && comma > 0) {
            encoded = encoded.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The profile picture could not be read.");
        }
    }
}
