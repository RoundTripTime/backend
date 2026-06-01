package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(description = "장소 일정 수정 요청")
public record UpdateItineraryItemRequest(
    @Schema(description = "일자 번호 (null 허용 — 미배치 풀로 이동)", nullable = true)
    Integer dayIndex,
    @Schema(description = "순서", nullable = true)
    Integer sortOrder,
    @Schema(description = "시작 시간 (HH:mm)", nullable = true, example = "09:00")
    LocalTime startTime,
    @Schema(description = "종료 시간 (HH:mm)", nullable = true, example = "11:00")
    LocalTime endTime
) {}
