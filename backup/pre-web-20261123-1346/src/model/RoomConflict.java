package src.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One overlapping room booking between two requests. Presented to staff so they can
 * coordinate; the system never declines or edits either request on its own.
 */
public class RoomConflict {

    private final long firstRequestId;
    private final String firstRequestCode;
    private final String firstBorrower;
    private final String firstStatus;
    private final String firstEquipment;
    private final long secondRequestId;
    private final String secondRequestCode;
    private final String secondBorrower;
    private final String secondStatus;
    private final String secondEquipment;
    private final String venue;
    private final LocalDate eventDate;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public RoomConflict(
            long firstRequestId, String firstRequestCode, String firstBorrower,
            String firstStatus, String firstEquipment,
            long secondRequestId, String secondRequestCode, String secondBorrower,
            String secondStatus, String secondEquipment,
            String venue, LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        this.firstRequestId = firstRequestId;
        this.firstRequestCode = firstRequestCode;
        this.firstBorrower = firstBorrower;
        this.firstStatus = firstStatus;
        this.firstEquipment = firstEquipment;
        this.secondRequestId = secondRequestId;
        this.secondRequestCode = secondRequestCode;
        this.secondBorrower = secondBorrower;
        this.secondStatus = secondStatus;
        this.secondEquipment = secondEquipment;
        this.venue = venue;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public long getFirstRequestId() { return firstRequestId; }
    public String getFirstRequestCode() { return firstRequestCode; }
    public String getFirstBorrower() { return firstBorrower; }
    public String getFirstStatus() { return firstStatus; }
    public String getFirstEquipment() { return firstEquipment; }
    public long getSecondRequestId() { return secondRequestId; }
    public String getSecondRequestCode() { return secondRequestCode; }
    public String getSecondBorrower() { return secondBorrower; }
    public String getSecondStatus() { return secondStatus; }
    public String getSecondEquipment() { return secondEquipment; }
    public String getVenue() { return venue; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
