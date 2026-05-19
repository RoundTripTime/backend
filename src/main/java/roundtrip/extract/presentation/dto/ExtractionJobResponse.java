package roundtrip.extract.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.extract.domain.entity.ExtractionJob;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "분석 잡 상태 응답")
public record ExtractionJobResponse(
    @Schema(description = "잡 고유 ID")
    UUID jobId,
    @Schema(description = "연결된 링크 ID")
    UUID sourceLinkId,
    @Schema(description = "현재 잡 상태", example = "done",
        allowableValues = {"pending", "processing", "done", "failed"})
    String jobStatus,
    @Schema(description = "추출된 장소 후보 수", example = "3")
    int signalCount,
    @Schema(description = "실패 시 에러 코드. 정상 시 null", nullable = true)
    String errorCode,
    @Schema(description = "처리 시작 일시 (ISO 8601)", example = "2024-12-01T10:00:05Z")
    OffsetDateTime startedAt,
    @Schema(description = "처리 완료 일시 (ISO 8601). 미완료 시 null", example = "2024-12-01T10:00:18Z", nullable = true)
    OffsetDateTime completedAt
) {
    public static ExtractionJobResponse from(ExtractionJob job) {
        return new ExtractionJobResponse(
                job.getId(),
                job.getSourceLinkId(),
                job.getJobStatus().name().toLowerCase(),
                job.getSignalCount(),
                job.getErrorCode(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
