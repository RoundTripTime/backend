package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.domain.repository.PlaceRepository.DiscoverRow;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "둘러보기 추천 장소 아이템")
public record DiscoverResult(
    @Schema(description = "장소 고유 ID")
    UUID placeId,
    @Schema(description = "정규화된 장소명", example = "츠키지 시장")
    String canonicalName,
    @Schema(description = "장소 카테고리", example = "시장")
    String category,
    @Schema(description = "위도", example = "35.655")
    BigDecimal latitude,
    @Schema(description = "경도", example = "139.77")
    BigDecimal longitude,
    @Schema(description = "국가 코드 (ISO 3166-1 alpha-2)", example = "JP")
    String countryCode,
    @Schema(description = "장소 썸네일 URL")
    String thumbnailUrl,
    @Schema(description = "썸네일 출처", example = "flickr",
        allowableValues = {"flickr", "wikimedia", "google_places"})
    String thumbnailSource,
    @Schema(description = "pgvector 코사인 유사도 (0.0~1.0)", example = "0.87")
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
