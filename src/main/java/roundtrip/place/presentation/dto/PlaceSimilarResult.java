package roundtrip.place.presentation.dto;

import roundtrip.place.domain.repository.PlaceRepository.PlaceSimilarRow;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceSimilarResult(
        UUID placeId,
        String canonicalName,
        String category,
        BigDecimal latitude,
        BigDecimal longitude,
        String thumbnailUrl,
        String thumbnailSource,
        double similarityScore
) {
    public static PlaceSimilarResult from(PlaceSimilarRow row) {
        return new PlaceSimilarResult(
                row.id(),
                row.canonicalName(),
                row.category() != null ? row.category().name().toLowerCase() : null,
                row.latitude(),
                row.longitude(),
                row.thumbnailUrl(),
                row.thumbnailSource() != null ? row.thumbnailSource().toLowerCase() : null,
                row.similarityScore()
        );
    }
}
