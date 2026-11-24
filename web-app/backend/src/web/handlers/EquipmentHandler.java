package web.handlers;

import src.model.Equipment;
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
            User actor = context.require(INVENTORY_READERS());
            if (actor == null) return;
            Map<String, Object> counters;
            if (isFacilityAdmin(actor.getRole())) {
                // Facility Admin monitors both inventories read-only.
                counters = services.inventoryRead.adminCounters(services.equipment.findAllEquipment());
            } else {
                String role = actor.getRole();
                counters = new LinkedHashMap<>();
                counters.put("types", services.equipment.countEquipmentTypesForRole(role));
                counters.put("availableUnits", services.equipment.countAvailableUnitsForRole(role));
                counters.put("totalUnits", services.equipment.countTotalUnitsForRole(role));
                counters.put("reservedToday", services.equipment.countReservedUnitsToday(role));
                counters.put("inUseToday", services.equipment.countInUseUnitsToday(role));
            }
            context.ok(counters);
            return;
        }

        if (method.equals("GET") && "available-for-schedule".equals(idPart)) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            LocalDate date = LocalDate.parse(context.query("date"));
            LocalTime start = LocalTime.parse(context.query("start"));
            LocalTime end = LocalTime.parse(context.query("end"));
            long exclude = 0;
            try {
                exclude = Long.parseLong(context.query("exclude"));
            } catch (NumberFormatException ignored) {
                // No request excluded.
            }
            List<Equipment> equipment = services.equipment.findAvailableEquipmentForSchedule(
                    date, start, end, exclude);
            if (isStaff(actor.getRole())) {
                // Staff scheduling follows the same custodian boundary as their inventory.
                String role = actor.getRole();
                equipment = equipment.stream()
                        .filter(item -> role.equalsIgnoreCase(item.getCustodianRole()))
                        .toList();
            }
            context.ok(equipment);
            return;
        }

        if (method.equals("GET") && idPart.isBlank()) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            String scope = context.query("scope");
            String role = actor.getRole();
            boolean privileged = isAdminOrDirector(role);
            if ("all".equals(scope) && !privileged) {
                context.error(403, "Your account is not allowed to view the full inventory.");
                return;
            }
            List<?> equipment;
            if (isStaff(role)) {
                // ICT and Facility staff only ever see their own custodian inventory.
                equipment = services.equipment.findEquipmentForCustodian(actor);
            } else if (privileged) {
                // Facility Admin and Campus Director keep the complete, shared inventory.
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
            User actor = context.require(INVENTORY_READERS());
            if (actor == null) return;
            if (isFacilityAdmin(actor.getRole())) {
                context.ok(services.inventoryRead.findActiveUsage(equipmentId));
            } else {
                context.ok(services.equipment.findActiveBorrowers(actor, equipmentId));
            }
            return;
        }

        if ("usage".equals(context.segment(2))) {
            context.error(403, "Equipment usage is read-only.");
            return;
        }

        if (method.equals("GET")) {
            User actor = context.require(ApiContext.ALL_ROLES());
            if (actor == null) return;
            String role = actor.getRole();
            List<Equipment> visible = isStaff(role)
                    ? services.equipment.findEquipmentForCustodian(actor)
                    : isAdminOrDirector(role)
                            ? services.equipment.findAllEquipment()
                            : services.equipment.findAvailableEquipment();
            List<Equipment> rows = visible.stream()
                    .filter(item -> item.getEquipmentId() == equipmentId)
                    .toList();
            if (rows.isEmpty()) {
                context.error(404, "Equipment not found.");
                return;
            }
            context.ok(rows.get(0));
            return;
        }

        // Inventory writes stay custodian-only. Facility Admin is read-only.
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

    private static boolean isFacilityAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
    }

    private static boolean isAdminOrDirector(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "DIRECTOR".equalsIgnoreCase(role);
    }

    /** Custodian staff manage inventory; Facility Admin only monitors it. */
    private static String[] INVENTORY_READERS() {
        return new String[] { "ICT_STAFF", "FACILITY_STAFF", "ADMIN" };
    }
}
