package src.model;

/**
 * Reservation-aware counts for one equipment scope and selected schedule.
 *
 * <p>Reserved and In Use are disjoint buckets. Moving a booking from staff
 * approval to released therefore moves a unit from Reserved to In Use rather
 * than subtracting it twice from Available.</p>
 */
public record EquipmentAvailabilitySummary(
        int totalStock,
        int reserved,
        int inUse,
        int available) {
}
