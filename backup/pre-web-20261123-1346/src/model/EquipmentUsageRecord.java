package src.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** One active released-not-returned equipment hand-over for an inventory item. */
public class EquipmentUsageRecord {

    private final long requestId;
    private final String requestCode;
    private final String borrowerName;
    private final String borrowerType;
    private final String department;
    private final String venue;
    private final LocalDate eventDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String groupStatus;
    private final LocalDateTime releasedAt;
    private final int releasedQuantity;
    private final int returnedQuantity;
    private final int missingQuantity;
    private final int inUseQuantity;

    public EquipmentUsageRecord(
            long requestId, String requestCode, String borrowerName, String borrowerType,
            String department, String venue, LocalDate eventDate, LocalTime startTime,
            LocalTime endTime, String groupStatus, LocalDateTime releasedAt,
            int releasedQuantity, int returnedQuantity, int missingQuantity, int inUseQuantity) {
        this.requestId = requestId;
        this.requestCode = requestCode;
        this.borrowerName = borrowerName;
        this.borrowerType = borrowerType;
        this.department = department;
        this.venue = venue;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.groupStatus = groupStatus;
        this.releasedAt = releasedAt;
        this.releasedQuantity = releasedQuantity;
        this.returnedQuantity = returnedQuantity;
        this.missingQuantity = missingQuantity;
        this.inUseQuantity = inUseQuantity;
    }

    public long getRequestId() { return requestId; }
    public String getRequestCode() { return requestCode; }
    public String getBorrowerName() { return borrowerName; }
    public String getBorrowerType() { return borrowerType; }
    public String getDepartment() { return department; }
    public String getVenue() { return venue; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getGroupStatus() { return groupStatus; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public int getReleasedQuantity() { return releasedQuantity; }
    public int getReturnedQuantity() { return returnedQuantity; }
    public int getMissingQuantity() { return missingQuantity; }
    public int getInUseQuantity() { return inUseQuantity; }
}
