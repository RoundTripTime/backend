package roundtrip.sourcelink.presentation.dto;

import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.sourcelink.application.SourceLinkService;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubmitSourceLinkResponse(
        UUID sourceLinkId,
        UUID jobId,
        String jobStatus,
        String sourceType,
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
