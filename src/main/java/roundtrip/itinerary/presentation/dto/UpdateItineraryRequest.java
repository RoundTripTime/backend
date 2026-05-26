package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "플랜 수정 요청 (변경할 항목만 포함)")
public record UpdateItineraryRequest(
    @Schema(description = "플랜 제목", nullable = true)
    String title,
    @Schema(description = "여행지", nullable = true)
    String destinationRegion,
    @Schema(description = "출발일", nullable = true)
    LocalDate startDate,
    @Schema(description = "도착일", nullable = true)
    LocalDate endDate,
    @Schema(description = "인원 수", nullable = true)
    Integer partySize,
    @Schema(description = "공개 범위", allowableValues = {"public", "private"}, nullable = true)
    String visibility,
    @Schema(description = "플랜 상태", allowableValues = {"draft", "confirmed", "completed"}, nullable = true)
    String status
) {}
