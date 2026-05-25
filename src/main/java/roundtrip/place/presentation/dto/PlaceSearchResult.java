package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "장소 검색 결과 아이템")
public record PlaceSearchResult(
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
    @Schema(description = "국가 코드 (ISO 3166-1 alpha-2)", example = "JP")
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
