package src.model;

public class EquipmentRequestItem {

    private final long equipmentId;
    private final String equipmentCode;
    private final String equipmentName;
    private final String category;
    private final int requestedQuantity;
    private final int assignedQuantity;
    private final int releasedQuantity;
    private final int returnedQuantity;
    private final String custodianRole;
    private final String groupStatus;
    private final int damagedQuantity;
    private final int missingQuantity;
    private final String conditionBefore;
    private final String conditionAfter;
    private final Long groupId;
    private final Long occurrenceId;
    private final String staffRemarks;
    private final String inspectionResult;

    public EquipmentRequestItem(
            long equipmentId,
            String equipmentCode,
            String equipmentName,
            String category,
            int requestedQuantity,
            int assignedQuantity,
            int releasedQuantity,
            int returnedQuantity) {
        this(equipmentId, equipmentCode, equipmentName, category,
                requestedQuantity, assignedQuantity, releasedQuantity, returnedQuantity,
                null, null);
    }

    public EquipmentRequestItem(
            long equipmentId,
            String equipmentCode,
            String equipmentName,
            String category,
            int requestedQuantity,
            int assignedQuantity,
            int releasedQuantity,
            int returnedQuantity,
            String custodianRole,
            String groupStatus) {
        this(equipmentId, equipmentCode, equipmentName, category,
                requestedQuantity, assignedQuantity, releasedQuantity, returnedQuantity,
                custodianRole, groupStatus, 0, 0, null, null);
    }

    public EquipmentRequestItem(
            long equipmentId,
            String equipmentCode,
            String equipmentName,
            String category,
            int requestedQuantity,
            int assignedQuantity,
            int releasedQuantity,
            int returnedQuantity,
            String custodianRole,
            String groupStatus,
            int damagedQuantity,
            int missingQuantity,
            String conditionBefore,
            String conditionAfter) {
        this(equipmentId, equipmentCode, equipmentName, category,
                requestedQuantity, assignedQuantity, releasedQuantity, returnedQuantity,
                custodianRole, groupStatus, damagedQuantity, missingQuantity,
                conditionBefore, conditionAfter, null, null, null, null);
    }

    public EquipmentRequestItem(
            long equipmentId,
            String equipmentCode,
            String equipmentName,
            String category,
            int requestedQuantity,
            int assignedQuantity,
            int releasedQuantity,
            int returnedQuantity,
            String custodianRole,
            String groupStatus,
            int damagedQuantity,
            int missingQuantity,
            String conditionBefore,
            String conditionAfter,
            Long groupId,
            Long occurrenceId,
            String staffRemarks,
            String inspectionResult) {
        this.damagedQuantity = damagedQuantity;
        this.missingQuantity = missingQuantity;
        this.conditionBefore = conditionBefore;
        this.conditionAfter = conditionAfter;
        this.equipmentId = equipmentId;
        this.equipmentCode = equipmentCode;
        this.equipmentName = equipmentName;
        this.category = category;
        this.requestedQuantity = requestedQuantity;
        this.assignedQuantity = assignedQuantity;
        this.releasedQuantity = releasedQuantity;
        this.returnedQuantity = returnedQuantity;
        this.custodianRole = custodianRole;
        this.groupStatus = groupStatus;
        this.groupId = groupId;
        this.occurrenceId = occurrenceId;
        this.staffRemarks = staffRemarks;
        this.inspectionResult = inspectionResult;
    }

    public long getEquipmentId() { return equipmentId; }
    public String getEquipmentCode() { return equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public String getCategory() { return category; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public int getAssignedQuantity() { return assignedQuantity; }
    public int getReleasedQuantity() { return releasedQuantity; }
    public int getReturnedQuantity() { return returnedQuantity; }
    public String getCustodianRole() { return custodianRole; }
    public String getGroupStatus() { return groupStatus; }
    public int getDamagedQuantity() { return damagedQuantity; }
    public int getMissingQuantity() { return missingQuantity; }
    public String getConditionBefore() { return conditionBefore; }
    public String getConditionAfter() { return conditionAfter; }
    public Long getGroupId() { return groupId; }
    public Long getOccurrenceId() { return occurrenceId; }
    public String getStaffRemarks() { return staffRemarks; }
    public String getInspectionResult() { return inspectionResult; }
}
