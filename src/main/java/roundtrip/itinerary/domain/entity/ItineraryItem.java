package roundtrip.itinerary.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "itinerary_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "itinerary_id", nullable = false, columnDefinition = "uuid")
    private UUID itineraryId;

    @Column(name = "place_id", nullable = false, columnDefinition = "uuid")
    private UUID placeId;

    @Column(name = "day_index")
    private Integer dayIndex;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "planned_duration_minutes")
    private Integer plannedDurationMinutes;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "source_candidate_id", columnDefinition = "uuid")
    private UUID sourceCandidateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static ItineraryItem create(UUID itineraryId, UUID placeId,
                                       Integer dayIndex, Integer sortOrder,
                                       Integer plannedDurationMinutes,
                                       LocalTime startTime, LocalTime endTime) {
        ItineraryItem item = new ItineraryItem();
        item.itineraryId = itineraryId;
        item.placeId = placeId;
        item.dayIndex = dayIndex;
        item.sortOrder = sortOrder;
        item.plannedDurationMinutes = plannedDurationMinutes;
        item.startTime = startTime;
        item.endTime = endTime;
        item.createdAt = OffsetDateTime.now();
        return item;
    }

    public void update(Integer dayIndex, Integer sortOrder, Integer plannedDurationMinutes,
                       LocalTime startTime, LocalTime endTime) {
        this.dayIndex = dayIndex;
        this.sortOrder = sortOrder;
        this.plannedDurationMinutes = plannedDurationMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void reorder(int dayIndex, int sortOrder) {
        this.dayIndex = dayIndex;
        this.sortOrder = sortOrder;
    }
}
