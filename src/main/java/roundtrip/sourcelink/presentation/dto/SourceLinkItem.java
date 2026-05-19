package roundtrip.sourcelink.presentation.dto;

import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceLinkItem(
        UUID sourceLinkId,
        String url,
        String sourceType,
        String title,
        String thumbnailUrl,
        String jobStatus,
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
