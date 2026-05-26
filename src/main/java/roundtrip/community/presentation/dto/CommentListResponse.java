package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.community.application.CommunityService.CommentListResult;

import java.util.List;
import java.util.UUID;

@Schema(description = "댓글 목록 응답")
public record CommentListResponse(
    @Schema(description = "댓글 목록") List<CommentResponse> items,
    @Schema(description = "다음 페이지 커서. null이면 마지막 페이지", nullable = true) UUID nextCursor
) {
    public static CommentListResponse from(CommentListResult result) {
        List<CommentResponse> items = result.items().stream()
            .map(CommentResponse::from)
            .toList();
        return new CommentListResponse(items, result.nextCursor());
    }
}
