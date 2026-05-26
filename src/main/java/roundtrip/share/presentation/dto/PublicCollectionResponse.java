package roundtrip.share.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.collection.domain.entity.Collection;
import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "공유 플레이스 응답")
public record PublicCollectionResponse(
    @Schema(description = "플레이스 고유 ID") UUID collectionId,
    @Schema(description = "플레이스 이름") String name,
    @Schema(description = "공개 범위") String visibility,
    @Schema(description = "포함된 장소 목록") List<PublicPlaceItem> places
) {
    public static PublicCollectionResponse from(Collection c, List<Place> places) {
        List<PublicPlaceItem> items = places.stream()
            .map(PublicPlaceItem::from)
            .toList();
        return new PublicCollectionResponse(c.getId(), c.getName(), c.getVisibility(), items);
    }

    @Schema(description = "장소 아이템")
    public record PublicPlaceItem(
        @Schema(description = "장소 고유 ID") UUID placeId,
        @Schema(description = "정규화된 장소명") String canonicalName,
        @Schema(description = "장소 카테고리") String category,
        @Schema(description = "위도") BigDecimal latitude,
        @Schema(description = "경도") BigDecimal longitude
    ) {
        public static PublicPlaceItem from(Place p) {
            return new PublicPlaceItem(
                p.getId(), p.getCanonicalName(),
                p.getCategory() != null ? p.getCategory().name().toLowerCase() : null,
                p.getLatitude(), p.getLongitude()
            );
        }
    }
}
