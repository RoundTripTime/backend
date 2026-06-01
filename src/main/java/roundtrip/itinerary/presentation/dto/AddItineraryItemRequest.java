package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

@Schema(description = "플랜에 장소 추가 요청")
public record AddItineraryItemRequest(
    @Schema(description = "추가할 장소 ID")
    @NotNull UUID placeId,
    @Schema(description = "일자 번호. 생략 시 미배치 풀로 추가", nullable = true)
    Integer dayIndex,
    @Schema(description = "해당 일자 내 순서", nullable = true)
    Integer sortOrder,
    @Schema(description = "예상 체류 시간 (분)", nullable = true)
    Integer plannedDurationMinutes,
    @Schema(description = "시작 시간 (HH:mm)", nullable = true, example = "09:00")
    LocalTime startTime,
    @Schema(description = "종료 시간 (HH:mm)", nullable = true, example = "11:00")
    LocalTime endTime
) {}
