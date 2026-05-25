package roundtrip.sourcelink.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.sourcelink.application.SourceLinkService;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "링크 제출 응답")
public record SubmitSourceLinkResponse(
    @Schema(description = "링크 고유 ID")
    UUID sourceLinkId,
    @Schema(description = "생성된 분석 잡 ID. /jobs/:job_id 폴링에 사용")
    UUID jobId,
    @Schema(description = "초기 잡 상태 (항상 pending)", example = "pending")
    String jobStatus,
    @Schema(description = "감지된 플랫폼 타입", example = "youtube_short",
        allowableValues = {"youtube_short", "instagram_reel"})
    String sourceType,
    @Schema(description = "제출 일시 (ISO 8601)", example = "2024-12-01T10:00:00Z")
    OffsetDateTime submittedAt
) {
    public static SubmitSourceLinkResponse from(SourceLinkService.SubmitResult result) {
        SourceLink link = result.sourceLink();
        ExtractionJob job = result.job();
        return new SubmitSourceLinkResponse(
                link.getId(),
                job.getId(),
                job.getJobStatus().name().toLowerCase(),
                link.getSourceType() != null ? link.getSourceType().name().toLowerCase() : null,
                link.getSubmittedAt()
        );
    }
}
