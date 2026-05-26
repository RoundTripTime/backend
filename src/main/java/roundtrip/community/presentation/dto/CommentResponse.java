package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.community.application.CommunityService.CommentDetail;
import roundtrip.user.domain.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "댓글 응답")
public record CommentResponse(
    @Schema(description = "댓글 고유 ID") UUID commentId,
    @Schema(description = "작성자 정보") PostResponse.AuthorInfo author,
    @Schema(description = "댓글 본문") String content,
    @Schema(description = "댓글 작성 일시") OffsetDateTime createdAt
) {
    public static CommentResponse from(CommentDetail detail) {
        User author = detail.author();
        var authorInfo = author != null
            ? new PostResponse.AuthorInfo(author.getId(), author.getNickname().value(), author.getAvatarUrl())
            : null;
        return new CommentResponse(
            detail.comment().getId(),
            authorInfo,
            detail.comment().getBody(),
            detail.comment().getCreatedAt()
        );
    }
}
