package web.handlers;

import src.model.User;
import web.main.AppServices;
import web.webapi.ApiContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Inventory, availability and derived stock counters (EquipmentService is reused unchanged). */
public final class EquipmentHandler {

    private EquipmentHandler() {
    }

    public static void handle(ApiContext context, AppServices services) throws Exception {
        String method = context.method();
        String idPart = context.segment(1);

        if (method.equals("GET") && "counters".equals(idPart)) {
            User actor = context.require(ApiContext.STAFF());
            if (actor == null) return;
            String role = actor.getRole();
            Map<String, Object> counters = new LinkedHashMap<>();
            counters.put("types", services.equipment.countEquipmentTypesForRole(role));
            counters.put("availableUnits", services.equipment.countAvailableUnitsForRole(role));
            counters.put("totalUnits", services.equipment.countTotalUnitsForRole(role));
            counters.put("reservedToday", services.equipment.countReservedUnitsToday(role));
            counters.put("inUseToday", services.equipment.countInUseUnitsToday(role));
            context.ok(counters);
            return;
        }

        if (method.equals("GET") && "available-for-schedule".equals(idPart)) {
            context.require(ApiContext.ALL_ROLES());
            if (context.user() == null) return;
            LocalDate date = LocalDate.parse(context.query("date"));
            LocalTime start = LocalTime.parse(context.query("start"));
            LocalTime end = LocalTime.parse(context.query("end"));
            long exclude = 0;
            try {
                exclude = Long.parseLong(context.query("exclude"));
            } catch (NumberFormatException ignored) {
                // No request excluded.
            }
            context.ok(services.equipment.findAvailableEquipmentForSchedule(
                    date, start, end, exclude));
            return;
        }

        if (method.equals("GET") && idPart.isBlank()) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            String scope = context.query("scope");
            String role = actor.getRole();
            List<?> equipment;
            if ("custodian".equals(scope) && isStaff(role)) {
                equipment = services.equipment.findEquipmentForCustodian(actor);
            } else if ("all".equals(scope) || isStaff(role)
                    || "ADMIN".equalsIgnoreCase(role) || "DIRECTOR".equalsIgnoreCase(role)) {
                equipment = services.equipment.findAllEquipment();
            } else {
                equipment = services.equipment.findAvailableEquipment();
            }
            context.ok(equipment);
            return;
        }

        if (method.equals("POST") && idPart.isBlank()) {
            User actor = context.require(ApiContext.STAFF());
            if (actor == null) return;
            services.equipment.saveEquipment(actor, null, context.text("code"),
                    context.text("name"), context.text("category"),
                    context.integer("totalQuantity"), context.text("status"));
            context.created(Map.of("message", "Equipment added."));
            return;
        }

        long equipmentId;
        try {
            equipmentId = Long.parseLong(idPart);
        } catch (NumberFormatException exception) {
            context.error(404, "Unknown equipment endpoint.");
            return;
        }

        if (method.equals("GET") && "usage".equals(context.segment(2))) {
            User actor = context.require(ApiContext.STAFF());
            if (actor == null) return;
            context.ok(services.equipment.findActiveBorrowers(actor, equipmentId));
            return;
        }

        if (method.equals("GET")) {
            context.require(ApiContext.ALL_ROLES());
            if (context.user() == null) return;
            List<?> rows = services.equipment.findAllEquipment().stream()
                    .filter(item -> item.getEquipmentId() == equipmentId)
                    .toList();
            if (rows.isEmpty()) {
                context.error(404, "Equipment not found.");
                return;
            }
            context.ok(rows.get(0));
            return;
        }

        User actor = context.require(ApiContext.STAFF());
        if (actor == null) return;

        if (method.equals("PUT")) {
            services.equipment.saveEquipment(actor, equipmentId, context.text("code"),
                    context.text("name"), context.text("category"),
                    context.integer("totalQuantity"), context.text("status"));
            context.ok(Map.of("message", "Equipment updated."));
            return;
        }

        if (method.equals("DELETE")) {
            services.equipment.deleteEquipment(actor, equipmentId);
            context.noContent();
            return;
        }

        context.error(404, "Unknown equipment endpoint.");
    }

    private static boolean isStaff(String role) {
        return "ICT_STAFF".equalsIgnoreCase(role) || "FACILITY_STAFF".equalsIgnoreCase(role);
    }
}
