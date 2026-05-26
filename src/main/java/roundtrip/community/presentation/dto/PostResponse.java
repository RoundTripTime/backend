package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.community.application.CommunityService.PostDetail;
import roundtrip.community.domain.repository.CommunityRepository.TaggedItineraryInfo;
import roundtrip.place.domain.entity.Place;
import roundtrip.user.domain.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "포스트 응답")
public record PostResponse(
    @Schema(description = "포스트 고유 ID") UUID postId,
    @Schema(description = "작성자 정보") AuthorInfo author,
    @Schema(description = "포스트 본문") String content,
    @Schema(description = "태그된 장소 목록") List<TaggedPlaceInfo> taggedPlaces,
    @Schema(description = "태그된 플랜 (없으면 null)", nullable = true) TaggedItineraryResponse taggedItinerary,
    @Schema(description = "좋아요 수") int likeCount,
    @Schema(description = "댓글 수") int commentCount,
    @Schema(description = "현재 로그인 사용자의 좋아요 여부") boolean isLiked,
    @Schema(description = "포스트 작성 일시") OffsetDateTime createdAt
) {
    public static PostResponse from(PostDetail detail) {
        User author = detail.author();
        var authorInfo = author != null
            ? new AuthorInfo(author.getId(), author.getNickname().value(), author.getAvatarUrl())
            : null;

        List<TaggedPlaceInfo> places = detail.taggedPlaces().stream()
            .map(TaggedPlaceInfo::from)
            .toList();

        TaggedItineraryResponse itinerary = detail.taggedItinerary() != null
            ? TaggedItineraryResponse.from(detail.taggedItinerary())
            : null;

        return new PostResponse(
            detail.post().getId(),
            authorInfo,
            detail.post().getBody(),
            places,
            itinerary,
            detail.post().getLikeCount(),
            detail.post().getCommentCount(),
            detail.isLiked(),
            detail.post().getCreatedAt()
        );
    }

    @Schema(description = "작성자 정보")
    public record AuthorInfo(
        @Schema(description = "작성자 고유 ID") UUID userId,
        @Schema(description = "작성자 닉네임") String nickname,
        @Schema(description = "작성자 프로필 이미지 URL") String avatarUrl
    ) {}

    @Schema(description = "태그된 장소 정보")
    public record TaggedPlaceInfo(
        @Schema(description = "장소 고유 ID") UUID placeId,
        @Schema(description = "정규화된 장소명") String canonicalName,
        @Schema(description = "장소 카테고리") String category,
        @Schema(description = "장소 썸네일 URL", nullable = true) String thumbnailUrl
    ) {
        public static TaggedPlaceInfo from(Place place) {
            return new TaggedPlaceInfo(
                place.getId(),
                place.getCanonicalName(),
                place.getCategory() != null ? place.getCategory().name().toLowerCase() : null,
                place.getThumbnailUrl()
            );
        }
    }

    @Schema(description = "태그된 플랜 정보")
    public record TaggedItineraryResponse(
        @Schema(description = "플랜 고유 ID") UUID itineraryId,
        @Schema(description = "플랜 제목") String title
    ) {
        public static TaggedItineraryResponse from(TaggedItineraryInfo info) {
            return new TaggedItineraryResponse(info.itineraryId(), info.title());
        }
    }
}
