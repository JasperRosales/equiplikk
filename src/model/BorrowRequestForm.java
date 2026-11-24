package src.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BorrowRequestForm {

    /** Longest borrower remark accepted; the UI mirrors this limit. */
    public static final int MAX_REMARKS_LENGTH = 1000;
    /** Longest urgency explanation accepted. */
    public static final int MAX_URGENT_REASON_LENGTH = 300;

    private final String venue;
    private final LocalDate eventDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String purpose;
    private final String participants;
    private final String personInCharge;
    private final String position;
    private final List<EquipmentRequestItem> equipmentItems;
    private final List<RequestOccurrence> occurrences;
    private final String borrowerRemarks;
    private final boolean urgent;
    private final String urgentReason;

    public BorrowRequestForm(
            String venue,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            String purpose,
            String participants,
            String personInCharge,
            String position,
            List<EquipmentRequestItem> equipmentItems) {
        this(venue, eventDate, startTime, endTime, purpose, participants,
                personInCharge, position, equipmentItems, List.of(), "");
    }

    public BorrowRequestForm(
            String venue,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            String purpose,
            String participants,
            String personInCharge,
            String position,
            List<EquipmentRequestItem> equipmentItems,
            List<RequestOccurrence> occurrences) {
        this(venue, eventDate, startTime, endTime, purpose, participants,
                personInCharge, position, equipmentItems, occurrences, "");
    }

    public BorrowRequestForm(
            String venue,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            String purpose,
            String participants,
            String personInCharge,
            String position,
            List<EquipmentRequestItem> equipmentItems,
            List<RequestOccurrence> occurrences,
            String borrowerRemarks) {
        this(venue, eventDate, startTime, endTime, purpose, participants,
                personInCharge, position, equipmentItems, occurrences,
                borrowerRemarks, false, "");
    }

    public BorrowRequestForm(
            String venue,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            String purpose,
            String participants,
            String personInCharge,
            String position,
            List<EquipmentRequestItem> equipmentItems,
            List<RequestOccurrence> occurrences,
            String borrowerRemarks,
            boolean urgent,
            String urgentReason) {
        this.venue = venue == null ? "" : venue.trim();
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.purpose = purpose == null ? "" : purpose.trim();
        this.participants = participants == null ? "" : participants.trim();
        this.personInCharge = personInCharge == null ? "" : personInCharge.trim();
        this.position = position == null ? "" : position.trim();
        this.equipmentItems = Collections.unmodifiableList(
                new ArrayList<>(equipmentItems == null ? List.of() : equipmentItems));
        List<RequestOccurrence> occ = occurrences == null ? List.of() : occurrences;
        this.occurrences = Collections.unmodifiableList(new ArrayList<>(occ));
        this.borrowerRemarks = borrowerRemarks == null ? "" : borrowerRemarks.trim();
        this.urgent = urgent;
        this.urgentReason = urgentReason == null ? "" : urgentReason.trim();
    }

    public String getVenue() { return venue; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getPurpose() { return purpose; }
    public String getParticipants() { return participants; }
    public String getPersonInCharge() { return personInCharge; }
    public String getPosition() { return position; }
    public List<EquipmentRequestItem> getEquipmentItems() { return equipmentItems; }
    public List<RequestOccurrence> getOccurrences() { return occurrences; }
    public String getBorrowerRemarks() { return borrowerRemarks; }
    public boolean isUrgent() { return urgent; }
    public String getUrgentReason() { return urgentReason; }
}
