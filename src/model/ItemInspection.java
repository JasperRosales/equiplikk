package src.model;

/** Staff input for one equipment item at release or return. */
public class ItemInspection {

    private final long equipmentId;
    private final int quantity;
    private final String condition;
    private final int damagedQuantity;
    private final int missingQuantity;
    private final String remarks;
    /** Reservation group for assignment inputs; null for legacy/ungrouped callers. */
    private final Long groupId;

    public ItemInspection(long equipmentId, int quantity, String condition,
            int damagedQuantity, int missingQuantity, String remarks) {
        this(equipmentId, quantity, condition, damagedQuantity, missingQuantity, remarks, null);
    }

    public ItemInspection(long equipmentId, int quantity, String condition,
            int damagedQuantity, int missingQuantity, String remarks, Long groupId) {
        this.equipmentId = equipmentId;
        this.quantity = quantity;
        this.condition = condition;
        this.damagedQuantity = damagedQuantity;
        this.missingQuantity = missingQuantity;
        this.remarks = remarks;
        this.groupId = groupId;
    }

    /** Assignment input: quantity to assign (0 = unable to fulfill). */
    public static ItemInspection assignment(long equipmentId, int quantity, String remarks) {
        return assignment(equipmentId, quantity, remarks, null);
    }

    /** Assignment input scoped to one reservation group. */
    public static ItemInspection assignment(long equipmentId, int quantity, String remarks,
            Long groupId) {
        return new ItemInspection(equipmentId, quantity, null, 0, 0, remarks, groupId);
    }

    /** Release input: condition before borrowing. */
    public static ItemInspection release(long equipmentId, String condition, String remarks) {
        return new ItemInspection(equipmentId, 0, condition, 0, 0, remarks);
    }

    /** Return input: condition after borrowing plus damaged/missing counts. */
    public static ItemInspection returned(long equipmentId, String condition,
            int damaged, int missing, String remarks) {
        return new ItemInspection(equipmentId, 0, condition, damaged, missing, remarks);
    }

    public long getEquipmentId() { return equipmentId; }
    public int getQuantity() { return quantity; }
    public String getCondition() { return condition; }
    public int getDamagedQuantity() { return damagedQuantity; }
    public int getMissingQuantity() { return missingQuantity; }
    public String getRemarks() { return remarks; }
    public Long getGroupId() { return groupId; }
}
