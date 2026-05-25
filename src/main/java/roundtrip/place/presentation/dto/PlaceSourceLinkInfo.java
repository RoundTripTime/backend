package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.util.UUID;

@Schema(description = "장소 원본 영상 정보 (상세 응답 내 포함)")
public record PlaceSourceLinkInfo(
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
    String thumbnailUrl
) {
    public static PlaceSourceLinkInfo from(SourceLink sl) {
        if (sl == null) return null;
        return new PlaceSourceLinkInfo(
                sl.getId(),
                sl.getUrl(),
                sl.getSourceType() != null ? sl.getSourceType().name().toLowerCase() : null,
                sl.getTitle(),
                sl.getThumbnailUrl()
        );
    }
}
