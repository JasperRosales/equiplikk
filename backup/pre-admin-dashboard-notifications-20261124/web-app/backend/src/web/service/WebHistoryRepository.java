package web.service;

import src.database.Database;
import src.model.RequestHistoryEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Read-only audit lookup. Visibility is checked by BorrowRequestController before use. */
public final class WebHistoryRepository {

    private WebHistoryRepository() {
    }

    public static List<RequestHistoryEvent> forRequest(long requestId) throws Exception {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT h.history_id, h.request_id, h.group_id, rg.custodian_role,
                            h.event_type, h.from_status, h.to_status, h.remarks,
                            h.created_at, COALESCE(u.full_name, '') AS actor
                     FROM request_history h
                     LEFT JOIN request_groups rg ON rg.group_id = h.group_id
                     LEFT JOIN users u ON u.user_id = h.actor_id
                     WHERE h.request_id = ?
                     ORDER BY h.created_at, h.history_id
                     """)) {
            statement.setLong(1, requestId);
            List<RequestHistoryEvent> events = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long groupId = result.getObject("group_id") == null
                            ? 0 : result.getLong("group_id");
                    Timestamp created = result.getTimestamp("created_at");
                    events.add(new RequestHistoryEvent(
                            result.getLong("history_id"),
                            result.getLong("request_id"),
                            groupId,
                            result.getString("custodian_role"),
                            result.getString("event_type"),
                            result.getString("from_status"),
                            result.getString("to_status"),
                            result.getString("remarks"),
                            created == null ? null : created.toLocalDateTime(),
                            result.getString("actor")));
                }
            }
            return events;
        }
    }
}
