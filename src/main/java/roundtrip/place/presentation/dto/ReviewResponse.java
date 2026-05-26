package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.domain.entity.PlaceReview;
import roundtrip.user.domain.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "리뷰 응답")
public record ReviewResponse(
    @Schema(description = "리뷰 고유 ID") UUID reviewId,
    @Schema(description = "작성자 정보") AuthorInfo author,
    @Schema(description = "평점 (1~5)") int rating,
    @Schema(description = "리뷰 본문") String content,
    @Schema(description = "리뷰 작성 일시") OffsetDateTime createdAt
) {
    public static ReviewResponse from(PlaceReview review, User author) {
        var authorInfo = author != null
            ? new AuthorInfo(author.getId(), author.getNickname().value(), author.getAvatarUrl())
            : null;
        return new ReviewResponse(
            review.getId(),
            authorInfo,
            review.getRating(),
            review.getBody(),
            review.getCreatedAt()
        );
    }

    @Schema(description = "작성자 정보")
    public record AuthorInfo(
        @Schema(description = "작성자 고유 ID") UUID userId,
        @Schema(description = "작성자 닉네임") String nickname,
        @Schema(description = "작성자 프로필 이미지 URL") String avatarUrl
    ) {}
}
