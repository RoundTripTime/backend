package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "플레이스 내 장소 아이템")
public record CollectionPlaceItem(
    @Schema(description = "장소 고유 ID")
    UUID placeId,
    @Schema(description = "정규화된 장소명", example = "시부야 스크램블 교차로")
    String canonicalName,
    @Schema(description = "장소 카테고리", example = "관광명소")
    String category,
    @Schema(description = "위도", example = "35.659513")
    BigDecimal latitude,
    @Schema(description = "경도", example = "139.70044")
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
