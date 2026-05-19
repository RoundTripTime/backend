package roundtrip.sourcelink.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "링크 목록 아이템")
public record SourceLinkItem(
    @Schema(description = "링크 고유 ID")
    UUID sourceLinkId,
    @Schema(description = "제출된 원본 URL", example = "https://youtube.com/shorts/abc123")
    String url,
    @Schema(description = "플랫폼 타입", example = "youtube_short",
        allowableValues = {"youtube_short", "instagram_reel"})
    String sourceType,
    @Schema(description = "영상 제목", example = "도쿄 숨은 맛집 VLOG")
    String title,
    @Schema(description = "영상 썸네일 URL")
    String thumbnailUrl,
    @Schema(description = "분석 잡 상태", example = "done",
        allowableValues = {"pending", "processing", "done", "failed"})
    String jobStatus,
    @Schema(description = "제출 일시 (ISO 8601)", example = "2024-12-01T10:00:00Z")
    OffsetDateTime submittedAt
) {
    public static SourceLinkItem of(SourceLink link, String jobStatus) {
        return new SourceLinkItem(
                link.getId(),
                link.getUrl(),
                link.getSourceType() != null ? link.getSourceType().name().toLowerCase() : null,
                link.getTitle(),
                link.getThumbnailUrl(),
                jobStatus,
                link.getSubmittedAt()
        );
    }
}
