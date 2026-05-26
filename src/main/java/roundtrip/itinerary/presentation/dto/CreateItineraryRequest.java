package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "플랜 생성 요청")
public record CreateItineraryRequest(
    @Schema(description = "플랜 제목", example = "도쿄 3박 4일")
    @NotBlank @Size(max = 100) String title,
    @Schema(description = "여행지", example = "도쿄, 일본")
    @NotBlank @Size(max = 100) String destinationRegion,
    @Schema(description = "출발일 (YYYY-MM-DD)", example = "2024-12-20")
    @NotNull LocalDate startDate,
    @Schema(description = "도착일 (YYYY-MM-DD)", example = "2024-12-23")
    @NotNull LocalDate endDate,
    @Schema(description = "여행 인원 수", example = "2")
    @NotNull @Min(1) Integer partySize
) {}
