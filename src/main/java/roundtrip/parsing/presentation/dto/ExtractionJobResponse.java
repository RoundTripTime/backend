package roundtrip.parsing.presentation.dto;

import roundtrip.parsing.domain.entity.ExtractionJob;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExtractionJobResponse(
        UUID jobId,
        UUID sourceLinkId,
        String jobStatus,
        int signalCount,
        String errorCode,
        OffsetDateTime startedAt,
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
