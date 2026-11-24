package web.service;

import src.model.BorrowRequestForm;
import src.model.EquipmentRequestItem;
import src.model.ItemInspection;
import src.model.RequestOccurrence;
import web.util.Json;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts JSON payloads into the existing desktop application's model DTOs. */
public final class WebForms {

    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("H:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    };

    private WebForms() {
    }

    public static BorrowRequestForm requestForm(Map<String, Object> body) {
        List<Object> rawOccurrences = Json.array(body.get("occurrences"));
        List<RequestOccurrence> occurrences = new ArrayList<>();
        for (Object entry : rawOccurrences) {
            Map<String, Object> item = Json.object(entry);
            occurrences.add(new RequestOccurrence(
                    date(first(item, "eventDate", "date")),
                    time(first(item, "startTime", "start")),
                    time(first(item, "endTime", "end")),
                    Json.text(item, "venue")));
        }
        if (occurrences.isEmpty()) {
            occurrences.add(new RequestOccurrence(
                    date(first(body, "eventDate", "date")),
                    time(first(body, "startTime", "start")),
                    time(first(body, "endTime", "end")),
                    Json.text(body, "venue")));
        }
        RequestOccurrence first = occurrences.get(0);

        List<Object> rawItems = Json.array(body.get("equipmentItems"));
        List<EquipmentRequestItem> items = new ArrayList<>();
        for (Object entry : rawItems) {
            Map<String, Object> item = Json.object(entry);
            long equipmentId = Json.number(item, "equipmentId") != 0
                    ? Json.number(item, "equipmentId") : Json.number(item, "id");
            int quantity = Json.integer(item, "requestedQuantity") != 0
                    ? Json.integer(item, "requestedQuantity") : Json.integer(item, "quantity");
            items.add(new EquipmentRequestItem(equipmentId, "", "", "", quantity, 0, 0, 0));
        }

        return new BorrowRequestForm(
                first(body, "venue").isEmpty() ? first.getVenue() : first(body, "venue"),
                first.getEventDate(),
                first.getStartTime(),
                first.getEndTime(),
                Json.text(body, "purpose"),
                Json.text(body, "participants"),
                Json.text(body, "personInCharge"),
                Json.text(body, "position"),
                items,
                occurrences,
                first(body, "borrowerRemarks", "remarks"),
                Json.flag(body, "urgent"),
                Json.text(body, "urgentReason"));
    }

    public static List<ItemInspection> assignments(List<Object> values) {
        List<ItemInspection> result = new ArrayList<>();
        for (Object entry : values) {
            Map<String, Object> item = Json.object(entry);
            result.add(ItemInspection.assignment(
                    Json.number(item, "equipmentId"),
                    Json.integer(item, "quantity"),
                    Json.text(item, "remarks")));
        }
        return result;
    }

    public static List<ItemInspection> releases(List<Object> values) {
        List<ItemInspection> result = new ArrayList<>();
        for (Object entry : values) {
            Map<String, Object> item = Json.object(entry);
            result.add(ItemInspection.release(
                    Json.number(item, "equipmentId"),
                    Json.text(item, "condition"),
                    Json.text(item, "remarks")));
        }
        return result;
    }

    public static List<ItemInspection> returns(List<Object> values) {
        List<ItemInspection> result = new ArrayList<>();
        for (Object entry : values) {
            Map<String, Object> item = Json.object(entry);
            result.add(ItemInspection.returned(
                    Json.number(item, "equipmentId"),
                    Json.text(item, "condition"),
                    Json.integer(item, "damagedQuantity"),
                    Json.integer(item, "missingQuantity"),
                    Json.text(item, "remarks")));
        }
        return result;
    }

    private static String first(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = Json.text(map, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private static LocalTime time(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter format : TIME_FORMATS) {
            try {
                return LocalTime.parse(text, format);
            } catch (Exception ignored) {
                // Try the next accepted format.
            }
        }
        throw new IllegalArgumentException("Invalid time: " + value);
    }
}
