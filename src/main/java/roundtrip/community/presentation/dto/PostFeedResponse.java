package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.community.application.CommunityService.FeedResult;

import java.util.List;
import java.util.UUID;

@Schema(description = "커뮤니티 피드 응답")
public record PostFeedResponse(
    @Schema(description = "포스트 목록") List<PostResponse> items,
    @Schema(description = "다음 페이지 커서. null이면 마지막 페이지", nullable = true) UUID nextCursor
) {
    public static PostFeedResponse from(FeedResult result) {
        List<PostResponse> items = result.items().stream()
            .map(PostResponse::from)
            .toList();
        return new PostFeedResponse(items, result.nextCursor());
    }
}
