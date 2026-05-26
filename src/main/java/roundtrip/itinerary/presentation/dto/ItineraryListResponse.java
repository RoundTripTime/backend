package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "플랜 목록 응답")
public record ItineraryListResponse(
    @Schema(description = "플랜 목록")
    List<ItineraryItem> items
) {
    @Schema(description = "플랜 목록 아이템")
    public record ItineraryItem(
        @Schema(description = "플랜 고유 ID")
        java.util.UUID itineraryId,
        @Schema(description = "플랜 제목")
        String title,
        @Schema(description = "여행지")
        String destinationRegion,
        @Schema(description = "출발일")
        java.time.LocalDate startDate,
        @Schema(description = "도착일")
        java.time.LocalDate endDate,
        @Schema(description = "여행 인원 수")
        int partySize,
        @Schema(description = "공개 범위")
        String visibility,
        @Schema(description = "플랜 상태")
        String status,
        @Schema(description = "포함된 장소 수")
        int placeCount,
        @Schema(description = "플랜 생성 일시")
        java.time.OffsetDateTime createdAt
    ) {
        public static ItineraryItem from(roundtrip.itinerary.domain.entity.Itinerary it, int placeCount) {
            return new ItineraryItem(
                    it.getId(), it.getTitle(), it.getDestinationRegion(),
                    it.getStartDate(), it.getEndDate(), it.getPartySize(),
                    it.getVisibility(), it.getStatus(), placeCount, it.getCreatedAt()
            );
        }
    }
}
