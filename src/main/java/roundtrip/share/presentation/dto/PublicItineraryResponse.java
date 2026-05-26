package roundtrip.share.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;
import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "공유 플랜 응답")
public record PublicItineraryResponse(
    @Schema(description = "플랜 고유 ID") UUID itineraryId,
    @Schema(description = "플랜 제목") String title,
    @Schema(description = "여행지") String destinationRegion,
    @Schema(description = "출발일") LocalDate startDate,
    @Schema(description = "도착일") LocalDate endDate,
    @Schema(description = "여행 인원 수") int partySize,
    @Schema(description = "일정 장소 목록") List<PublicItemResponse> items
) {
    public static PublicItineraryResponse from(Itinerary it, List<ItemWithPlace> itemsWithPlaces) {
        List<PublicItemResponse> items = itemsWithPlaces.stream()
            .map(PublicItemResponse::from)
            .toList();
        return new PublicItineraryResponse(
            it.getId(), it.getTitle(), it.getDestinationRegion(),
            it.getStartDate(), it.getEndDate(), it.getPartySize(), items
        );
    }

    public record ItemWithPlace(ItineraryItem item, Place place) {}

    @Schema(description = "일정 장소 아이템")
    public record PublicItemResponse(
        @Schema(description = "장소 고유 ID") UUID placeId,
        @Schema(description = "정규화된 장소명") String canonicalName,
        @Schema(description = "장소 카테고리") String category,
        @Schema(description = "위도") BigDecimal latitude,
        @Schema(description = "경도") BigDecimal longitude,
        @Schema(description = "여행 일차", nullable = true) Integer dayIndex,
        @Schema(description = "해당 일차 내 순서", nullable = true) Integer sortOrder,
        @Schema(description = "예상 체류 시간(분)", nullable = true) Integer plannedDurationMinutes
    ) {
        public static PublicItemResponse from(ItemWithPlace iwp) {
            var item = iwp.item();
            var place = iwp.place();
            return new PublicItemResponse(
                place.getId(), place.getCanonicalName(),
                place.getCategory() != null ? place.getCategory().name().toLowerCase() : null,
                place.getLatitude(), place.getLongitude(),
                item.getDayIndex(), item.getSortOrder(), item.getPlannedDurationMinutes()
            );
        }
    }
}
