package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "리뷰 작성 요청")
public record CreateReviewRequest(
    @Schema(description = "평점 (1~5 정수)", example = "4")
    @NotNull @Min(1) @Max(5) Short rating,

    @Schema(description = "리뷰 본문 (최대 500자)", example = "뷰가 정말 좋아요!", nullable = true)
    @Size(max = 500) String content
) {}
