package roundtrip.candidate.presentation.dto;

import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceInfo(
        UUID id,
        String canonicalName,
        BigDecimal latitude,
        BigDecimal longitude,
        String category,
        String countryCode,
        String kakaoPlaceId,
        String thumbnailUrl
) {
    public static PlaceInfo from(Place place) {
        if (place == null) return null;
        return new PlaceInfo(
                place.getId(),
                place.getCanonicalName(),
                place.getLatitude(),
                place.getLongitude(),
                place.getCategory() != null ? place.getCategory().name().toLowerCase() : null,
                place.getCountryCode(),
                place.getKakaoPlaceId(),
                place.getThumbnailUrl()
        );
    }
}
