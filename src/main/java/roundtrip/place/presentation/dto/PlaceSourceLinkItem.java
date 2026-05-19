package roundtrip.place.presentation.dto;

import roundtrip.place.application.PlaceService.SourceLinkResult;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlaceSourceLinkItem(
        UUID sourceLinkId,
        String url,
        String platform,
        String title,
        String thumbnailUrl,
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
