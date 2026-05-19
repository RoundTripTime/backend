package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.collection.application.CollectionService.CollectionWithPlaces;
import roundtrip.collection.domain.entity.Collection;

import java.util.List;
import java.util.UUID;

@Schema(description = "플레이스 내 장소 목록 응답")
public record CollectionPlaceListResponse(
    @Schema(description = "플레이스 고유 ID")
    UUID collectionId,
    @Schema(description = "플레이스 이름", example = "일본")
    String name,
    @Schema(description = "공개 범위", example = "public", allowableValues = {"public", "private"})
    String visibility,
    @Schema(description = "포함된 장소 목록")
    List<CollectionPlaceItem> places
) {
    public static CollectionPlaceListResponse from(CollectionWithPlaces result) {
        Collection c = result.collection();
        List<CollectionPlaceItem> items = result.places().stream()
                .map(CollectionPlaceItem::from)
                .toList();
        return new CollectionPlaceListResponse(c.getId(), c.getName(), c.getVisibility(), items);
    }
}
