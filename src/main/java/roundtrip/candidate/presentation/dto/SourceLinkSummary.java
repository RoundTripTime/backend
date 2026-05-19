package roundtrip.candidate.presentation.dto;

import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceLinkSummary(
        UUID id,
        String sourceType,
        String url,
        String title,
        String thumbnailUrl,
        String status,
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
