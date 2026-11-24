package src.model;

import java.time.LocalDateTime;

/**
 * One immutable audit-trail row from {@code request_history}. Used by the ICT
 * System Admin account details view to show the full approval trail of every
 * request without changing the existing approval workflow.
 */
public class RequestHistoryEvent {

    private final long historyId;
    private final long requestId;
    private final Long groupId;
    private final String custodianRole;
    private final String eventType;
    private final String fromStatus;
    private final String toStatus;
    private final String remarks;
    private final LocalDateTime createdAt;
    private final String actorName;

    public RequestHistoryEvent(
            long historyId, long requestId, Long groupId, String custodianRole,
            String eventType, String fromStatus, String toStatus, String remarks,
            LocalDateTime createdAt, String actorName) {

        this.historyId = historyId;
        this.requestId = requestId;
        this.groupId = groupId;
        this.custodianRole = custodianRole;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.remarks = remarks;
        this.createdAt = createdAt;
        this.actorName = actorName;
    }

    public long getHistoryId() { return historyId; }
    public long getRequestId() { return requestId; }
    public Long getGroupId() { return groupId; }
    public String getCustodianRole() { return custodianRole; }
    public String getEventType() { return eventType; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getRemarks() { return remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getActorName() { return actorName; }
}
