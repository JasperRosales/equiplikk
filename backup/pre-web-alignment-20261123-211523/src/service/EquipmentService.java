package src.service;

import src.database.Database;
import src.model.Equipment;
import src.model.EquipmentUsageRecord;
import src.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class EquipmentService {

    public List<Equipment> findAvailableEquipment() throws SQLException {
        return findEquipment(false, -1, null, null, null);
    }

    public List<Equipment> findAvailableEquipmentExcluding(long requestId) throws SQLException {
        return findEquipment(false, requestId, null, null, null);
    }

    public List<Equipment> findAvailableEquipmentForSchedule(
            java.time.LocalDate eventDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            long excludedRequestId) throws SQLException {
        if (eventDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("A date and time are required to check availability.");
        }
        return findEquipment(
                false, excludedRequestId, eventDate, startTime, endTime);
    }

    public List<Equipment> findAllEquipment() throws SQLException {
        return findEquipment(true, -1, null, null, null);
    }

    public List<Equipment> findEquipmentForCustodian(String role) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        List<Equipment> equipment = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + EQUIPMENT_COLUMNS + """
                        FROM equipment e
                        WHERE e.active = TRUE AND e.custodian_role = ?
                        ORDER BY e.category, e.equipment_name, e.equipment_code
                        """)) {
            statement.setString(1, normalizedRole);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    equipment.add(mapEquipment(result));
                }
            }
        }
        return equipment;
    }

    /**
     * Active hand-overs of one equipment type, scoped to the signed-in custodian role.
     * Quantity is the same released-minus-returned-minus-missing value used by the
     * inventory's In use count, so the detail list always agrees with the card.
     */
    public List<EquipmentUsageRecord> findActiveBorrowers(long equipmentId, String role)
            throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement check = connection.prepareStatement("""
                    SELECT custodian_role FROM equipment
                    WHERE equipment_id = ? AND active = TRUE
                    """)) {
                check.setLong(1, equipmentId);
                try (ResultSet result = check.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Equipment was not found.");
                    }
                    if (!normalizedRole.equals(result.getString("custodian_role"))) {
                        throw new SecurityException(
                                "This equipment belongs to another department's inventory.");
                    }
                }
            }

            List<EquipmentUsageRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
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
                      AND rg.custodian_role = ?
                      AND rg.status IN ('RELEASED', 'RECEIVED')
                      AND GREATEST(ri.released_quantity - ri.returned_quantity
                                   - ri.missing_quantity, 0) > 0
                    ORDER BY COALESCE(ro.event_date, br.event_date),
                             COALESCE(ro.start_time, br.start_time), br.request_id
                    """)) {
                statement.setLong(1, equipmentId);
                statement.setString(2, normalizedRole);
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

    public void saveEquipment(
            User actor,
            Long equipmentId,
            String code,
            String name,
            String category,
            int totalQuantity,
            String status) throws SQLException {
        String custodianRole = requireActorCustodian(actor);
        if (code == null || code.isBlank()
                || name == null || name.isBlank()
                || category == null || category.isBlank()) {
            throw new IllegalArgumentException(
                    "Equipment code, name, and category are required.");
        }
        if (code.trim().length() > 30 || name.trim().length() > 120
                || category.trim().length() > 60) {
            throw new IllegalArgumentException("Some equipment details exceed the allowed length.");
        }
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        if (!"AVAILABLE".equals(status)
                && !"UNDER_MAINTENANCE".equals(status)
                && !"RETIRED".equals(status)) {
            throw new IllegalArgumentException("Select a valid equipment status.");
        }

        if (equipmentId == null) {
            try (Connection connection = Database.getConnection();
                    PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO equipment (
                                equipment_code, equipment_name, category, total_quantity,
                                status, custodian_role, created_by
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """)) {
                statement.setString(1, code.trim());
                statement.setString(2, name.trim());
                statement.setString(3, category.trim());
                statement.setInt(4, totalQuantity);
                statement.setString(5, status);
                statement.setString(6, custodianRole);
                statement.setLong(7, actor.getUserId());
                statement.executeUpdate();
            }
        } else {
            try (Connection connection = Database.getConnection();
                    PreparedStatement statement = connection.prepareStatement("""
                            UPDATE equipment
                            SET equipment_code = ?, equipment_name = ?, category = ?,
                                total_quantity = ?, status = ?
                            WHERE equipment_id = ? AND custodian_role = ? AND active = TRUE
                            """)) {
                statement.setString(1, code.trim());
                statement.setString(2, name.trim());
                statement.setString(3, category.trim());
                statement.setInt(4, totalQuantity);
                statement.setString(5, status);
                statement.setLong(6, equipmentId);
                statement.setString(7, custodianRole);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalArgumentException(
                            "Equipment was not found or is outside your inventory.");
                }
            }
        }
    }

    public void deleteEquipment(User actor, long equipmentId) throws SQLException {
        String custodianRole = requireActorCustodian(actor);
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE equipment
                        SET active = FALSE
                        WHERE equipment_id = ? AND custodian_role = ? AND active = TRUE
                        """)) {
            statement.setLong(1, equipmentId);
            statement.setString(2, custodianRole);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException(
                        "Equipment was not found or is outside your inventory.");
            }
        }
    }

    public int countEquipmentTypesForRole(String role) throws SQLException {
        return findEquipmentForCustodian(role).size();
    }

    public int countAvailableUnitsForRole(String role) throws SQLException {
        int available = 0;
        for (Equipment item : findEquipmentForCustodian(role)) {
            if ("AVAILABLE".equals(item.getStatus())) {
                available += item.getAvailableQuantity();
            }
        }
        return available;
    }

    public int countTotalUnitsForRole(String role) throws SQLException {
        return countEquipmentValue(role, """
                SELECT COALESCE(SUM(total_quantity), 0)
                FROM equipment
                WHERE active = TRUE AND custodian_role = ?
                """);
    }

    public int countReservedUnitsToday(String role) throws SQLException {
        return countEquipmentValue(role, """
                SELECT COALESCE(SUM(GREATEST(ri.assigned_quantity - ri.released_quantity, 0)), 0)
                FROM request_items ri
                JOIN request_groups rg ON rg.group_id = ri.group_id
                JOIN borrow_requests br ON br.request_id = ri.request_id
                LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
                WHERE rg.custodian_role = ? AND rg.status = 'ASSIGNED'
                  AND br.status IN ('APPROVED', 'RELEASED')
                  AND COALESCE(ro.event_date, br.event_date) = CURRENT_DATE
                """);
    }

    public int countInUseUnitsToday(String role) throws SQLException {
        return countEquipmentValue(role, """
                SELECT COALESCE(SUM(GREATEST(ri.released_quantity - ri.returned_quantity, 0)), 0)
                FROM request_items ri
                JOIN request_groups rg ON rg.group_id = ri.group_id
                JOIN borrow_requests br ON br.request_id = ri.request_id
                LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
                WHERE rg.custodian_role = ? AND rg.status IN ('RELEASED', 'RECEIVED')
                  AND COALESCE(ro.event_date, br.event_date) = CURRENT_DATE
                """);
    }

    private int countEquipmentValue(String role, String sql) throws SQLException {
        String normalizedRole = requireCustodianRole(role);
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedRole);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String requireActorCustodian(User actor) {
        if (actor == null) {
            throw new SecurityException("A signed-in staff account is required.");
        }
        return requireCustodianRole(actor.getRole());
    }

    private String requireCustodianRole(String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!"ICT_STAFF".equals(normalizedRole)
                && !"FACILITY_STAFF".equals(normalizedRole)) {
            throw new SecurityException(
                    "Only ICT or facility staff can manage equipment inventory.");
        }
        return normalizedRole;
    }

    private List<Equipment> findEquipment(
            boolean includeUnavailable,
            long excludedRequestId,
            java.time.LocalDate eventDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime) throws SQLException {
        boolean scheduleAware = eventDate != null && startTime != null && endTime != null;
        String sql = "SELECT " + EQUIPMENT_COLUMNS + """
                FROM equipment e
                WHERE e.active = TRUE
                ORDER BY e.category, e.equipment_name, e.equipment_code
                """;
        List<Equipment> base = new ArrayList<>();
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                base.add(mapEquipment(result));
            }
            if (scheduleAware) {
                List<Equipment> scheduled = new ArrayList<>();
                Long excluded = excludedRequestId <= 0 ? null : excludedRequestId;
                for (Equipment item : base) {
                    int available = findAvailableQuantity(connection, item.getEquipmentId(),
                            excluded, eventDate, startTime, endTime);
                    if (available <= 0 && !includeUnavailable) {
                        continue;
                    }
                    scheduled.add(new Equipment(
                            item.getEquipmentId(), item.getEquipmentCode(),
                            item.getEquipmentName(), item.getCategory(),
                            item.getTotalQuantity(), item.getDamagedQuantity(),
                            item.getLostQuantity(), available, item.getStatus(),
                            item.getCustodianRole(), item.getInUseQuantity(),
                            item.getLocation()));
                }
                return scheduled;
            }
        }
        List<Equipment> equipment = new ArrayList<>();
        for (Equipment item : base) {
            if (includeUnavailable
                    || ("AVAILABLE".equals(item.getStatus())
                            && item.getAvailableQuantity() > 0)) {
                equipment.add(item);
            }
        }
        return equipment;
    }

    /** Units currently out with borrowers (released or received, not yet inspected). */
    private static final String IN_USE_SQL = """
            COALESCE((
                SELECT SUM(GREATEST(ri.released_quantity - ri.returned_quantity
                        - ri.missing_quantity, 0))
                FROM request_items ri
                JOIN request_groups rg ON rg.group_id = ri.group_id
                WHERE ri.assigned_equipment_id = e.equipment_id
                  AND rg.status IN ('RELEASED', 'RECEIVED')
            ), 0)
            """;

    /** Units on hand: usable stock minus units currently out with borrowers. */
    private static final String ON_HAND_SQL =
            "GREATEST(e.total_quantity - e.damaged_quantity - e.lost_quantity - "
                    + IN_USE_SQL + ", 0)";

    /** Current location, derived from equipment status and released/borrowed transactions. */
    private static final String LOCATION_SQL = """
            CASE
                WHEN e.status = 'UNDER_MAINTENANCE' THEN 'Under Maintenance'
                WHEN e.status = 'RETIRED' THEN 'Retired'
                WHEN EXISTS (
                    SELECT 1 FROM request_items ri
                    JOIN request_groups rg ON rg.group_id = ri.group_id
                    JOIN borrow_requests br ON br.request_id = ri.request_id
                    LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
                    WHERE ri.assigned_equipment_id = e.equipment_id
                      AND rg.status IN ('RELEASED', 'RECEIVED')
                      AND COALESCE(ro.event_date, br.event_date)
                          + COALESCE(ro.end_time, br.end_time)
                          < (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::timestamp
                ) THEN 'Under Inspection'
                WHEN EXISTS (
                    SELECT 1 FROM request_items ri
                    JOIN request_groups rg ON rg.group_id = ri.group_id
                    JOIN borrow_requests br ON br.request_id = ri.request_id
                    JOIN users u ON u.user_id = br.borrower_id
                    WHERE ri.assigned_equipment_id = e.equipment_id
                      AND rg.status IN ('RELEASED', 'RECEIVED')
                      AND u.borrower_type = 'FACULTY'
                ) THEN 'Borrowed by Faculty'
                WHEN EXISTS (
                    SELECT 1 FROM request_items ri
                    JOIN request_groups rg ON rg.group_id = ri.group_id
                    WHERE ri.assigned_equipment_id = e.equipment_id
                      AND rg.status IN ('RELEASED', 'RECEIVED')
                ) THEN 'Borrowed by Student'
                WHEN e.custodian_role = 'ICT_STAFF' THEN 'Available at ICT Office'
                ELSE 'Available at Facility Office'
            END
            """;

    private static final String EQUIPMENT_COLUMNS = """
            e.equipment_id, e.equipment_code, e.equipment_name,
            e.category, e.total_quantity, e.damaged_quantity,
            e.lost_quantity, e.status, e.custodian_role,
            """ + ON_HAND_SQL + " AS available_quantity, "
            + IN_USE_SQL + " AS in_use_quantity,\n"
            + LOCATION_SQL + " AS location\n";

    /**
     * Units that can still be promised for a schedule. Two schedules conflict when
     * they overlap in time ({@code newStart < existingEnd AND newEnd > existingStart}).
     * Only groups that already reserve stock (approved, released, or received) are
     * counted, so a brand-new pending request never blocks an overlapping one on its
     * own; staff still see both as a conflict before approving.
     *
     * <p>With no schedule the method answers the physical question used at release:
     * how many usable units are on hand right now.</p>
     */
    public static int findAvailableQuantity(
            Connection connection,
            long equipmentId,
            Long excludedRequestId,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime) throws SQLException {
        if (eventDate == null || startTime == null || endTime == null) {
            return findOnHandQuantity(connection, equipmentId);
        }
        int usable = findUsableQuantity(connection, equipmentId);
        int reserved = findReservedQuantity(
                connection, equipmentId, excludedRequestId, eventDate, startTime, endTime);
        return Math.max(usable - reserved, 0);
    }

    /** Usable stock: total units minus damaged and lost units, never negative. */
    public static int findUsableQuantity(Connection connection, long equipmentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT GREATEST(e.total_quantity - e.damaged_quantity"
                        + " - e.lost_quantity, 0) FROM equipment e "
                        + "WHERE e.equipment_id = ? AND e.active = TRUE")) {
            statement.setLong(1, equipmentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /**
     * Units already promised to other requests that overlap this schedule. The same
     * {@code newStart < existingEnd AND newEnd > existingStart} rule is used for
     * equipment and rooms, so staff see one consistent definition of a conflict.
     */
    public static int findReservedQuantity(
            Connection connection,
            long equipmentId,
            Long excludedRequestId,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime) throws SQLException {
        if (eventDate == null || startTime == null || endTime == null) {
            return 0;
        }
        String sql = """
                SELECT COALESCE(SUM(GREATEST(ri.assigned_quantity, 0)), 0)
                FROM request_items ri
                JOIN request_groups rg ON rg.group_id = ri.group_id
                JOIN borrow_requests br ON br.request_id = ri.request_id
                LEFT JOIN request_occurrences ro ON ro.occurrence_id = rg.occurrence_id
                WHERE ri.assigned_equipment_id = ?
                  AND br.deleted_at IS NULL
                  AND rg.status IN ('ASSIGNED', 'RELEASED', 'RECEIVED')
                  AND (COALESCE(ro.event_date, br.event_date)
                          + COALESCE(ro.start_time, br.start_time)) < ?
                  AND (COALESCE(ro.event_date, br.event_date)
                          + COALESCE(ro.end_time, br.end_time)) > ?
                """
                + (excludedRequestId == null ? "" : "  AND ri.request_id <> ?\n");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, equipmentId);
            statement.setTimestamp(2, Timestamp.valueOf(eventDate.atTime(endTime)));
            statement.setTimestamp(3, Timestamp.valueOf(eventDate.atTime(startTime)));
            if (excludedRequestId != null) {
                statement.setLong(4, excludedRequestId);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    /** Units physically out with borrowers right now, independent of any schedule. */
    public static int findInUseQuantity(Connection connection, long equipmentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + IN_USE_SQL + " FROM equipment e WHERE e.equipment_id = ?")) {
            statement.setLong(1, equipmentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    /** Physical units available right now, used when staff confirm a release. */
    public static int findOnHandQuantity(Connection connection, long equipmentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ON_HAND_SQL + " FROM equipment e "
                        + "WHERE e.equipment_id = ? AND e.active = TRUE")) {
            statement.setLong(1, equipmentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    public int countAvailableEquipmentTypes() throws SQLException {
        return findAvailableEquipment().size();
    }

    public int countAllEquipmentTypes() throws SQLException {
        return findAllEquipment().size();
    }

    public int countAvailableUnits() throws SQLException {
        int available = 0;
        for (Equipment item : findAllEquipment()) {
            available += item.getAvailableQuantity();
        }
        return available;
    }

    private Equipment mapEquipment(ResultSet result) throws SQLException {
        return new Equipment(
                result.getLong("equipment_id"),
                result.getString("equipment_code"),
                result.getString("equipment_name"),
                result.getString("category"),
                result.getInt("total_quantity"),
                result.getInt("damaged_quantity"),
                result.getInt("lost_quantity"),
                result.getInt("available_quantity"),
                result.getString("status"),
                result.getString("custodian_role"),
                result.getInt("in_use_quantity"),
                result.getString("location"));
    }
}
