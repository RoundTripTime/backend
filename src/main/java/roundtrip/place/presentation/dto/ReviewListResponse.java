package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "리뷰 목록 응답")
public record ReviewListResponse(
    @Schema(description = "리뷰 목록") List<ReviewResponse> items,
    @Schema(description = "다음 페이지 커서. null이면 마지막 페이지", nullable = true) UUID nextCursor
) {}
