package roundtrip.place.presentation.dto;

import roundtrip.place.domain.repository.PlaceRepository.DiscoverRow;

import java.math.BigDecimal;
import java.util.UUID;

public record DiscoverResult(
        UUID placeId,
        String canonicalName,
        String category,
        BigDecimal latitude,
        BigDecimal longitude,
        String countryCode,
        String thumbnailUrl,
        String thumbnailSource,
        double similarityScore
) {
    public static DiscoverResult from(DiscoverRow row) {
        return new DiscoverResult(
                row.id(),
                row.canonicalName(),
                row.category() != null ? row.category().name().toLowerCase() : null,
                row.latitude(),
                row.longitude(),
                row.countryCode(),
                row.thumbnailUrl(),
                row.thumbnailSource() != null ? row.thumbnailSource().toLowerCase() : null,
                row.similarityScore()
        );
    }
}
