package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.itinerary.domain.entity.Itinerary;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "플랜 상세 응답")
public record ItineraryResponse(
    @Schema(description = "플랜 고유 ID")
    UUID itineraryId,
    @Schema(description = "플랜 제목")
    String title,
    @Schema(description = "여행지")
    String destinationRegion,
    @Schema(description = "출발일")
    LocalDate startDate,
    @Schema(description = "도착일")
    LocalDate endDate,
    @Schema(description = "여행 인원 수")
    int partySize,
    @Schema(description = "공개 범위")
    String visibility,
    @Schema(description = "플랜 상태")
    String status,
    @Schema(description = "포함된 장소 목록")
    List<ItineraryItemResponse> items,
    @Schema(description = "플랜 생성 일시")
    OffsetDateTime createdAt
) {
    public static ItineraryResponse from(Itinerary it, List<ItineraryItemResponse> items) {
        return new ItineraryResponse(
                it.getId(), it.getTitle(), it.getDestinationRegion(),
                it.getStartDate(), it.getEndDate(), it.getPartySize(),
                it.getVisibility(), it.getStatus(), items, it.getCreatedAt()
        );
    }
}
