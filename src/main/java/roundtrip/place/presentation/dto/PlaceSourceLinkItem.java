package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.application.PlaceService.SourceLinkResult;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "장소 출처 영상 아이템")
public record PlaceSourceLinkItem(
    @Schema(description = "링크 고유 ID")
    UUID sourceLinkId,
    @Schema(description = "원본 영상 URL", example = "https://youtube.com/shorts/abc123")
    String url,
    @Schema(description = "플랫폼 타입", example = "youtube_short",
        allowableValues = {"youtube_short", "instagram_reel"})
    String platform,
    @Schema(description = "영상 제목", example = "도쿄 숨은 맛집 VLOG")
    String title,
    @Schema(description = "영상 썸네일 URL")
    String thumbnailUrl,
    @Schema(description = "링크 제출 일시 (ISO 8601)", example = "2024-12-01T10:00:00Z")
    OffsetDateTime submittedAt
) {
    public static PlaceSourceLinkItem from(SourceLinkResult result) {
        SourceLink sl = result.sourceLink();
        return new PlaceSourceLinkItem(
                sl.getId(),
                sl.getUrl(),
                sl.getSourceType() != null ? sl.getSourceType().name().toLowerCase() : null,
                sl.getTitle(),
                sl.getThumbnailUrl(),
                sl.getSubmittedAt()
        );
    }
}
