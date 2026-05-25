package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.domain.repository.PlaceRepository.PlaceSimilarRow;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "유사 장소 아이템")
public record PlaceSimilarResult(
    @Schema(description = "장소 고유 ID")
    UUID placeId,
    @Schema(description = "정규화된 장소명", example = "블루보틀 시부야점")
    String canonicalName,
    @Schema(description = "장소 카테고리", example = "카페")
    String category,
    @Schema(description = "위도", example = "35.661")
    BigDecimal latitude,
    @Schema(description = "경도", example = "139.699")
    BigDecimal longitude,
    @Schema(description = "장소 썸네일 URL")
    String thumbnailUrl,
    @Schema(description = "썸네일 출처", example = "wikimedia",
        allowableValues = {"flickr", "wikimedia", "google_places"})
    String thumbnailSource,
    @Schema(description = "pgvector 코사인 유사도 (0.0~1.0, 높을수록 유사)", example = "0.91")
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
