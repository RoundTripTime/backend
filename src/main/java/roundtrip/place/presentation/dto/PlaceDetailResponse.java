package roundtrip.place.presentation.dto;

import roundtrip.place.application.PlaceService.PlaceDetailResult;
import roundtrip.place.domain.entity.Place;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlaceDetailResponse(
        UUID placeId,
        String canonicalName,
        BigDecimal latitude,
        BigDecimal longitude,
        String category,
        String countryCode,
        String googlePlaceId,
        String kakaoPlaceId,
        String thumbnailUrl,
        String thumbnailSource,
        PlaceSourceLinkInfo sourceLink,
        String evidence,
        OffsetDateTime createdAt
) {
    public static PlaceDetailResponse from(PlaceDetailResult result) {
        Place p = result.place();
        SourceLink sl = result.sourceLink();
        return new PlaceDetailResponse(
                p.getId(),
                p.getCanonicalName(),
                p.getLatitude(),
                p.getLongitude(),
                p.getCategory() != null ? p.getCategory().name().toLowerCase() : null,
                p.getCountryCode(),
                p.getGooglePlaceId(),
                p.getKakaoPlaceId(),
                p.getThumbnailUrl(),
                p.getThumbnailSource() != null ? p.getThumbnailSource().name().toLowerCase() : null,
                PlaceSourceLinkInfo.from(sl),
                p.getEvidence(),
                p.getCreatedAt()
        );
    }
}
