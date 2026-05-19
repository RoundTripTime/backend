package roundtrip.collection.presentation.dto;

import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.util.UUID;

public record CollectionPlaceItem(
        UUID placeId,
        String canonicalName,
        String category,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public static CollectionPlaceItem from(Place p) {
        return new CollectionPlaceItem(
                p.getId(),
                p.getCanonicalName(),
                p.getCategory() != null ? p.getCategory().name().toLowerCase() : null,
                p.getLatitude(),
                p.getLongitude()
        );
    }
}
