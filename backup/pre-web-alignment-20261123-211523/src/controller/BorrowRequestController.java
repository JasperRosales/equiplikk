package src.controller;

import src.model.BorrowRequest;
import src.model.BorrowRequestForm;
import src.model.ItemInspection;
import src.model.RequestHistoryEvent;
import src.model.User;
import src.service.BorrowRequestService;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BorrowRequestController {

    private final BorrowRequestService requestService;

    public BorrowRequestController(BorrowRequestService requestService) {
        this.requestService = requestService;
    }

    public int countQueueForRole(String role) throws Exception {
        return requestService.countQueueForRole(role);
    }

    public List<BorrowRequest> findActiveRequests(long borrowerId) throws Exception {
        return requestService.findActiveRequests(borrowerId);
    }

    public List<BorrowRequest> findBorrowingHistory(long borrowerId) throws Exception {
        return requestService.findBorrowingHistory(borrowerId);
    }

    public List<BorrowRequest> findAllForBorrower(long borrowerId) throws Exception {
        return requestService.findAllForBorrower(borrowerId);
    }

    /**
     * ICT System Admin view of one account's complete request history. Includes
     * records hidden from the borrower's own list, but never any other account's.
     */
    public List<BorrowRequest> findAllForAccount(User actor, long borrowerId) throws Exception {
        requireRole(actor, "ICT_STAFF");
        if (borrowerId <= 0) {
            throw new IllegalArgumentException("Invalid borrower account.");
        }
        return requestService.findAllForBorrowerIncludingRemoved(borrowerId);
    }

    /** Approval/audit trail for that same account's requests, scoped by borrower id. */
    public List<RequestHistoryEvent> findHistoryForAccount(
            User actor, long borrowerId, java.util.Collection<Long> requestIds) throws Exception {
        requireRole(actor, "ICT_STAFF");
        if (borrowerId <= 0) {
            throw new IllegalArgumentException("Invalid borrower account.");
        }
        return requestService.findHistoryEvents(borrowerId, requestIds);
    }

    public BorrowRequest findRequest(long borrowerId, long requestId) throws Exception {
        return requestService.findRequest(borrowerId, requestId);
    }

    /** Derived availability, conflict, and Remarks notes for staff review. */
    public List<String> findAvailabilityNotes(
            User actor, long requestId, String scopeRole) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF", "ADMIN", "DIRECTOR");
        return requestService.findAvailabilityNotes(requestId, scopeRole);
    }

    public long createRequest(long borrowerId, BorrowRequestForm form) throws Exception {
        return requestService.createRequest(borrowerId, form);
    }

    public void updateRequest(
            long borrowerId, long requestId, BorrowRequestForm form) throws Exception {
        requestService.updateRequest(borrowerId, requestId, form);
    }

    public void deleteRequest(long borrowerId, long requestId) throws Exception {
        requestService.deleteRequest(borrowerId, requestId);
    }

    /** Undo a borrower deletion while it is still inside the recovery window. */
    public void restoreRequest(long borrowerId, long requestId) throws Exception {
        requestService.restoreRequest(borrowerId, requestId);
    }

    public void deleteRequestAsAdmin(User actor, long requestId, String reason)
            throws Exception {
        requireRole(actor, "ICT_STAFF");
        requestService.deleteRequestAsAdmin(
                actor.getRole(), actor.getUserId(), requestId, reason);
    }

    /** Undo an ICT System Admin deletion while it is still inside the recovery window. */
    public void restoreRequestAsAdmin(User actor, long requestId) throws Exception {
        requireRole(actor, "ICT_STAFF");
        requestService.restoreRequestAsAdmin(actor.getRole(), actor.getUserId(), requestId);
    }

    public int countActiveForBorrower(long borrowerId) throws Exception {
        return requestService.countActiveForBorrower(borrowerId);
    }

    public int countPendingForBorrower(long borrowerId) throws Exception {
        return requestService.countPendingForBorrower(borrowerId);
    }

    public int countBorrowedForBorrower(long borrowerId) throws Exception {
        return requestService.countBorrowedForBorrower(borrowerId);
    }

    public int countByStatus(String status) throws Exception {
        return requestService.countByStatus(status);
    }

    public List<BorrowRequest> findStaffQueue(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        return requestService.findStaffQueue(actor.getRole());
    }

    public List<BorrowRequest> findAdminQueue(User actor) throws Exception {
        requireRole(actor, "ADMIN");
        return requestService.findAdminQueue();
    }

    public List<BorrowRequest> findDirectorQueue(User actor) throws Exception {
        requireRole(actor, "DIRECTOR");
        return requestService.findDirectorQueue();
    }

    /** All requests the actor may monitor; staff only see their department's. */
    public List<BorrowRequest> findAllRequests(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF", "ADMIN", "DIRECTOR");
        return requestService.findAllRequests(actor.getRole());
    }

    /** Requests with activity today, used for role dashboard counters. */
    public List<BorrowRequest> findDailyActivity(User actor, java.time.LocalDate day)
            throws Exception {
        if (actor == null) return List.of();
        return requestService.findDailyActivity(actor.getRole(), actor.getUserId(), day);
    }

    /** Department groups assigned to the staff actor today, counted once per group. */
    public int countAssignedGroupsToday(User actor, java.time.LocalDate day) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        return requestService.countAssignedGroupsToday(actor.getRole(), day);
    }

    /** Requests that reached final approval on the given Manila day, counted once. */
    public int countFullyApprovedOn(User actor, java.time.LocalDate day) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF", "ADMIN", "DIRECTOR");
        return requestService.countFullyApprovedOn(day);
    }

    /** Overlapping room bookings visible to the actor; never modifies either request. */
    public List<src.model.RoomConflict> findRoomConflicts(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF", "ADMIN", "DIRECTOR");
        return requestService.findRoomConflicts(actor.getRole());
    }

    /** Every request the actor can see, used for dashboard counters. */
    public List<BorrowRequest> findVisibleRequests(User actor) throws Exception {
        if (actor != null && "BORROWER".equalsIgnoreCase(actor.getRole())) {
            return requestService.findAllForBorrower(actor.getUserId());
        }
        return findAllRequests(actor);
    }

    public BorrowRequest findRequestForUser(User actor, long requestId) throws Exception {
        if (actor == null) {
            throw new SecurityException("A signed-in account is required.");
        }
        BorrowRequest request = requestService.findRequestForUser(
                actor.getRole(), actor.getUserId(), requestId);
        if (request == null) {
            throw new IllegalArgumentException(
                    "The request was not found or is not available to your account.");
        }
        return request;
    }

    public List<BorrowRequest> findScheduleForRole(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        return requestService.findScheduleForRole(actor.getRole());
    }

    public List<BorrowRequest> findHistoryForRole(User actor) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        return requestService.findHistoryForRole(actor.getRole());
    }

    public void assignStaffRequest(User actor, long requestId) throws Exception {
        assignStaffRequest(actor, requestId, null);
    }

    public Map<Long, Integer> availableAssignmentQuantities(
            User actor, long requestId) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        return requestService.findAssignmentAvailability(actor.getRole(), requestId);
    }

    public void assignStaffRequest(
            User actor, long requestId, List<ItemInspection> assignments) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.assignStaffRequest(
                actor.getRole(), actor.getUserId(), requestId, assignments);
    }

    public void declineStaffRequest(
            User actor, long requestId, String remarks) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.declineStaffRequest(
                actor.getRole(), actor.getUserId(), requestId, remarks);
    }

    public void markStaffRequestNeedsAction(
            User actor, long requestId, String remarks) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.markStaffRequestNeedsAction(
                actor.getRole(), actor.getUserId(), requestId, remarks);
    }

    public void reviewByAdmin(
            User actor, long requestId, boolean approve, String remarks) throws Exception {
        requireRole(actor, "ADMIN");
        requestService.reviewByAdmin(actor.getUserId(), requestId, approve, remarks);
    }

    public void reviewByDirector(
            User actor, long requestId, boolean approve, String remarks) throws Exception {
        requireRole(actor, "DIRECTOR");
        requestService.reviewByDirector(actor.getUserId(), requestId, approve, remarks);
    }

    public void releaseStaffAssignment(
            User actor, long requestId, List<ItemInspection> conditions) throws Exception {
        releaseStaffAssignment(actor, requestId, null, conditions);
    }

    /** @param groupId the actor role's stored reservation group selected for release. */
    public void releaseStaffAssignment(
            User actor, long requestId, Long groupId, List<ItemInspection> conditions)
            throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.releaseStaffAssignment(
                actor.getRole(), actor.getUserId(), requestId, groupId, conditions);
    }

    public void recordStaffReturn(
            User actor, long requestId, List<ItemInspection> inspections) throws Exception {
        requireRole(actor, "ICT_STAFF", "FACILITY_STAFF");
        requestService.recordStaffReturn(
                actor.getRole(), actor.getUserId(), requestId, inspections);
    }

    public void receiveByBorrower(User actor, long requestId) throws Exception {
        if (actor == null || !"BORROWER".equalsIgnoreCase(actor.getRole())) {
            throw new SecurityException("Only borrowers can confirm receipt.");
        }
        requestService.receiveByBorrower(actor.getUserId(), requestId);
    }

    private static final Set<String> REQUEST_FORM_EXPORT_ROLES = Set.of(
            "ICT_STAFF", "FACILITY_STAFF", "ADMIN", "DIRECTOR");

    /**
     * Download is limited to four authorized roles. Facility Admin and Campus Director
     * may print the original form at any stage; equipment staff keep the existing
     * approved/completed restriction.
     */
    public boolean canExportRequestForm(User actor, BorrowRequest request) {
        if (actor == null || request == null || actor.getRole() == null) {
            return false;
        }
        String role = actor.getRole().trim().toUpperCase(java.util.Locale.ROOT);
        if (!REQUEST_FORM_EXPORT_ROLES.contains(role)) {
            return false;
        }
        if ("ADMIN".equals(role) || "DIRECTOR".equals(role)) {
            return true;
        }
        String status = request.getStatus();
        return "APPROVED".equals(status) || "COMPLETED".equals(status);
    }

    /** Re-checks role and the database status at the moment of export; never trust a visible button. */
    public void verifyRequestFormExport(User actor, BorrowRequest request) throws Exception {
        requireRole(actor, REQUEST_FORM_EXPORT_ROLES.toArray(String[]::new));
        if (request == null || request.getRequestId() <= 0) {
            throw new IllegalArgumentException("A valid request is required.");
        }
        BorrowRequest current = findRequestForUser(actor, request.getRequestId());
        if (!canExportRequestForm(actor, current)) {
            throw new SecurityException("This request form is not available to your account.");
        }
    }

    private void requireRole(User actor, String... allowedRoles) {
        if (actor == null) {
            throw new SecurityException("A signed-in account is required.");
        }
        for (String role : allowedRoles) {
            if (role.equalsIgnoreCase(actor.getRole())) {
                return;
            }
        }
        throw new SecurityException("Your account is not allowed to perform this action.");
    }
}
