package roundtrip.itinerary.presentation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.itinerary.domain.entity.ItineraryItem;

import java.time.LocalTime;
import java.util.UUID;

@Schema(description = "플랜 아이템 응답")
public record ItineraryItemResponse(
    @Schema(description = "일정 아이템 고유 ID")
    UUID itemId,
    @Schema(description = "장소 고유 ID")
    UUID placeId,
    @Schema(description = "장소명")
    String placeName,
    @Schema(description = "배치된 여행 일차. null이면 미배치 풀", nullable = true)
    Integer dayIndex,
    @Schema(description = "해당 일차 내 순서", nullable = true)
    Integer sortOrder,
    @Schema(description = "예상 체류 시간 (분)", nullable = true)
    Integer plannedDurationMinutes,
    @Schema(description = "시작 시간", nullable = true, example = "09:00")
    @JsonFormat(pattern = "HH:mm")
    LocalTime startTime,
    @Schema(description = "종료 시간", nullable = true, example = "11:00")
    @JsonFormat(pattern = "HH:mm")
    LocalTime endTime
) {
    public static ItineraryItemResponse from(ItineraryItem item, String placeName) {
        return new ItineraryItemResponse(
                item.getId(), item.getPlaceId(), placeName,
                item.getDayIndex(), item.getSortOrder(),
                item.getPlannedDurationMinutes(),
                item.getStartTime(), item.getEndTime()
        );
    }
}
