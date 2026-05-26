package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좋아요 수 응답")
public record LikeCountResponse(
    @Schema(description = "좋아요 수", example = "13") int likeCount
) {}
