package roundtrip.candidate.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "원본 링크 요약 정보")
public record SourceLinkSummary(
    @Schema(description = "링크 고유 ID")
    UUID id,
    @Schema(description = "플랫폼 타입", example = "youtube_short",
        allowableValues = {"youtube_short", "instagram_reel"})
    String sourceType,
    @Schema(description = "원본 URL", example = "https://youtube.com/shorts/abc123")
    String url,
    @Schema(description = "영상 제목", example = "도쿄 숨은 맛집 VLOG")
    String title,
    @Schema(description = "영상 썸네일 URL")
    String thumbnailUrl,
    @Schema(description = "링크 처리 상태", example = "done")
    String status,
    @Schema(description = "제출 일시 (ISO 8601)", example = "2024-12-01T10:00:00Z")
    OffsetDateTime submittedAt
) {
    public static SourceLinkSummary from(SourceLink link) {
        return new SourceLinkSummary(
                link.getId(),
                link.getSourceType() != null ? link.getSourceType().name().toLowerCase() : null,
                link.getUrl(),
                link.getTitle(),
                link.getThumbnailUrl(),
                link.getStatus().name().toLowerCase(),
                link.getSubmittedAt()
        );
    }
}
