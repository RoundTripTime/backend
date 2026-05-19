package roundtrip.place.presentation.dto;

import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceSearchResult(
        UUID placeId,
        String canonicalName,
        String category,
        BigDecimal latitude,
        BigDecimal longitude,
        String countryCode
) {
    public static PlaceSearchResult from(Place p) {
        return new PlaceSearchResult(
                p.getId(),
                p.getCanonicalName(),
                p.getCategory() != null ? p.getCategory().name().toLowerCase() : null,
                p.getLatitude(),
                p.getLongitude(),
                p.getCountryCode()
        );
    }
}
