package roundtrip.place.presentation.dto;

import roundtrip.sourcelink.domain.entity.SourceLink;

import java.util.UUID;

public record PlaceSourceLinkInfo(
        UUID sourceLinkId,
        String url,
        String platform,
        String title,
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
