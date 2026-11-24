package web.service;

import src.database.Database;
import src.model.Equipment;
import src.model.EquipmentUsageRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web-only read model for Facility Admin inventory monitoring.
 *
 * <p>The shared EquipmentService intentionally limits custodian-scoped operations to
 * ICT_STAFF and FACILITY_STAFF. Facility Admin may view both inventories but must never
 * modify them, so the HTTP layer uses these read-only queries instead of loosening the
 * desktop service permissions.</p>
 */
public final class WebInventoryReadService {

    private static final String RESERVED_TODAY_SQL = """
            SELECT COALESCE(SUM(GREATEST(ri.assigned_quantity - ri.released_quantity, 0)), 0)
            FROM request_items ri
            JOIN request_groups rg ON rg.group_id = ri.group_id
            JOIN borrow_requests br ON br.request_id = ri.request_id
            LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
            WHERE rg.status = 'ASSIGNED'
              AND br.status IN ('APPROVED', 'RELEASED')
              AND COALESCE(ro.event_date, br.event_date) = CURRENT_DATE
            """;

    private static final String IN_USE_TODAY_SQL = """
            SELECT COALESCE(SUM(GREATEST(ri.released_quantity - ri.returned_quantity, 0)), 0)
            FROM request_items ri
            JOIN request_groups rg ON rg.group_id = ri.group_id
            JOIN borrow_requests br ON br.request_id = ri.request_id
            LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
            WHERE rg.status IN ('RELEASED', 'RECEIVED')
              AND COALESCE(ro.event_date, br.event_date) = CURRENT_DATE
            """;

    private static final String ACTIVE_USAGE_SQL = """
            SELECT br.request_id, br.request_code, u.full_name AS borrower_name,
                   u.borrower_type, u.department,
                   COALESCE(ro.venue, br.venue) AS venue,
                   COALESCE(ro.event_date, br.event_date) AS event_date,
                   COALESCE(ro.start_time, br.start_time) AS start_time,
                   COALESCE(ro.end_time, br.end_time) AS end_time,
                   rg.status AS group_status, rg.released_at,
                   ri.released_quantity, ri.returned_quantity,
                   ri.missing_quantity,
                   GREATEST(ri.released_quantity - ri.returned_quantity
                            - ri.missing_quantity, 0) AS in_use_quantity
            FROM request_items ri
            JOIN request_groups rg ON rg.group_id = ri.group_id
            JOIN borrow_requests br ON br.request_id = ri.request_id
            JOIN users u ON u.user_id = br.borrower_id
            LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
            WHERE ri.assigned_equipment_id = ?
              AND rg.status IN ('RELEASED', 'RECEIVED')
              AND GREATEST(ri.released_quantity - ri.returned_quantity
                           - ri.missing_quantity, 0) > 0
            ORDER BY COALESCE(ro.event_date, br.event_date),
                     COALESCE(ro.start_time, br.start_time), br.request_id
            """;

    public WebInventoryReadService() {
    }

    /** Counters across both custodian inventories, derived from live records. */
    public Map<String, Object> adminCounters(List<Equipment> equipment) throws Exception {
        int totalUnits = 0;
        int availableUnits = 0;
        for (Equipment item : equipment) {
            totalUnits += item.getTotalQuantity();
            if ("AVAILABLE".equalsIgnoreCase(item.getStatus())) {
                availableUnits += item.getAvailableQuantity();
            }
        }

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("types", equipment.size());
        counters.put("totalUnits", totalUnits);
        counters.put("availableUnits", availableUnits);
        try (Connection connection = Database.getConnection()) {
            counters.put("reservedToday", scalar(connection, RESERVED_TODAY_SQL));
            counters.put("inUseToday", scalar(connection, IN_USE_TODAY_SQL));
        }
        return counters;
    }

    /**
     * Active hand-overs for one equipment type across both inventories. One row is
     * returned per request group, so multiple borrowers of the same type stay separate.
     */
    public List<EquipmentUsageRecord> findActiveUsage(long equipmentId) throws Exception {
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement check = connection.prepareStatement("""
                    SELECT 1 FROM equipment
                    WHERE equipment_id = ? AND active = TRUE
                    """)) {
                check.setLong(1, equipmentId);
                try (ResultSet result = check.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Equipment was not found.");
                    }
                }
            }

            List<EquipmentUsageRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ACTIVE_USAGE_SQL)) {
                statement.setLong(1, equipmentId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        Timestamp released = result.getTimestamp("released_at");
                        java.sql.Date eventDate = result.getDate("event_date");
                        java.sql.Time startTime = result.getTime("start_time");
                        java.sql.Time endTime = result.getTime("end_time");
                        records.add(new EquipmentUsageRecord(
                                result.getLong("request_id"),
                                result.getString("request_code"),
                                result.getString("borrower_name"),
                                result.getString("borrower_type"),
                                result.getString("department"),
                                result.getString("venue"),
                                eventDate == null ? null : eventDate.toLocalDate(),
                                startTime == null ? null : startTime.toLocalTime(),
                                endTime == null ? null : endTime.toLocalTime(),
                                result.getString("group_status"),
                                released == null ? null : released.toLocalDateTime(),
                                result.getInt("released_quantity"),
                                result.getInt("returned_quantity"),
                                result.getInt("missing_quantity"),
                                result.getInt("in_use_quantity")));
                    }
                }
            }
            return records;
        }
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }
}
