package web.service;

import src.model.BorrowRequest;
import src.model.DepartmentStatus;
import src.model.EquipmentRequestItem;
import src.model.RequestOccurrence;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Generates the printable request form from the same BorrowRequest model the desktop uses. */
public final class RequestExportService {

    private RequestExportService() {
    }

    public static byte[] html(BorrowRequest request) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>").append(escape(request.getRequestCode())).append("</title>")
                .append("<style>")
                .append("body{font-family:Poppins,Segoe UI,Arial,sans-serif;color:#2B1A1A;margin:36px auto;max-width:850px}")
                .append("h1{color:#660000;margin-bottom:4px}h2{color:#660000;font-size:16px;margin-top:28px;border-bottom:1px solid #E4D8D8;padding-bottom:6px}")
                .append("table{width:100%;border-collapse:collapse;font-size:13px}th,td{border:1px solid #E4D8D8;padding:8px;text-align:left}th{background:#FAEFEF}")
                .append(".muted{color:#6E5A5A}.badge{display:inline-block;background:#660000;color:#fff;padding:4px 10px;border-radius:12px;font-size:12px}")
                .append("@media print{body{margin:0}}")
                .append("</style></head><body>");
        html.append("<h1>EquipLink Borrow Request</h1>")
                .append("<p class=\"muted\">").append(escape(request.getRequestCode()))
                .append(" &middot; submitted ").append(escape(String.valueOf(request.getSubmittedAt())))
                .append("</p>")
                .append("<p><span class=\"badge\">").append(escape(request.getStatus()))
                .append("</span></p>");

        table(html, "Request details", new String[][] {
                { "Borrower", request.getBorrowerName() },
                { "Department", request.getBorrowerDepartment() },
                { "Type", request.getBorrowerType() },
                { "Venue", request.getVenue() },
                { "Event date", String.valueOf(request.getEventDate()) },
                { "Time", request.getStartTime() + " - " + request.getEndTime() },
                { "Purpose", request.getPurpose() },
                { "Participants", request.getParticipants() },
                { "Person in charge", request.getPersonInCharge() },
                { "Position", request.getPosition() },
                { "Borrower remarks", request.getBorrowerRemarks() },
                { "Urgent", request.isUrgent() ? "Yes - " + request.getUrgentReason() : "No" }
        });

        if (!request.getOccurrences().isEmpty()) {
            html.append("<h2>Schedules</h2><table><tr><th>Date</th><th>Start</th><th>End</th><th>Venue</th></tr>");
            for (RequestOccurrence occurrence : request.getOccurrences()) {
                html.append("<tr><td>").append(escape(String.valueOf(occurrence.getEventDate())))
                        .append("</td><td>").append(escape(String.valueOf(occurrence.getStartTime())))
                        .append("</td><td>").append(escape(String.valueOf(occurrence.getEndTime())))
                        .append("</td><td>").append(escape(occurrence.getVenue()))
                        .append("</td></tr>");
            }
            html.append("</table>");
        }

        html.append("<h2>Equipment</h2><table><tr><th>Code</th><th>Equipment</th><th>Category</th>")
                .append("<th>Requested</th><th>Assigned</th><th>Released</th><th>Returned</th></tr>");
        for (EquipmentRequestItem item : request.getEquipmentItems()) {
            html.append("<tr><td>").append(escape(item.getEquipmentCode()))
                    .append("</td><td>").append(escape(item.getEquipmentName()))
                    .append("</td><td>").append(escape(item.getCategory()))
                    .append("</td><td>").append(item.getRequestedQuantity())
                    .append("</td><td>").append(item.getAssignedQuantity())
                    .append("</td><td>").append(item.getReleasedQuantity())
                    .append("</td><td>").append(item.getReturnedQuantity())
                    .append("</td></tr>");
        }
        html.append("</table>");

        List<DepartmentStatus> departments = request.getDepartments();
        if (departments != null && !departments.isEmpty()) {
            html.append("<h2>Department decisions</h2><table><tr><th>Department</th><th>Status</th>")
                    .append("<th>Remarks</th><th>Released</th><th>Inspected</th></tr>");
            for (DepartmentStatus department : departments) {
                html.append("<tr><td>").append(escape(department.getCustodianRole()))
                        .append("</td><td>").append(escape(department.getStatus()))
                        .append("</td><td>").append(escape(department.getRemarks()))
                        .append("</td><td>").append(escape(String.valueOf(department.getReleasedAt())))
                        .append("</td><td>").append(escape(String.valueOf(department.getInspectedAt())))
                        .append("</td></tr>");
            }
            html.append("</table>");
        }
        html.append("</body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void table(StringBuilder html, String heading, String[][] rows) {
        html.append("<h2>").append(escape(heading)).append("</h2><table>");
        for (String[] row : rows) {
            html.append("<tr><th>").append(escape(row[0])).append("</th><td>")
                    .append(escape(row[1]) == null ? "" : escape(row[1])).append("</td></tr>");
        }
        html.append("</table>");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
