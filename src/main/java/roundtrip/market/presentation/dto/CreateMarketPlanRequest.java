package roundtrip.market.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "플랜 마켓 등록 요청")
public record CreateMarketPlanRequest(
    @Schema(description = "등록할 플랜 ID") @NotNull UUID itineraryId,
    @Schema(description = "마켓 노출 제목 (최대 50자)") @NotBlank @Size(max = 50) String title,
    @Schema(description = "플랜 소개 (최대 500자)") @NotBlank @Size(max = 500) String description,
    @Schema(description = "한 줄 요약 (최대 100자)") @NotBlank @Size(max = 100) String highlight,
    @Schema(description = "좋았던 점 (최대 300자)") @NotBlank @Size(max = 300) String pros,
    @Schema(description = "아쉬웠던 점 (최대 300자)") @NotBlank @Size(max = 300) String cons,
    @Schema(description = "추가 팁 (최대 300자)", nullable = true) @Size(max = 300) String tips
) {}
