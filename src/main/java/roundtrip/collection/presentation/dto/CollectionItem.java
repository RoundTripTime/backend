package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.collection.application.CollectionService.CollectionSummary;
import roundtrip.collection.domain.entity.Collection;

import java.util.UUID;

@Schema(description = "플레이스 목록 아이템")
public record CollectionItem(
    @Schema(description = "플레이스 고유 ID")
    UUID collectionId,
    @Schema(description = "플레이스 이름", example = "일본")
    String name,
    @Schema(description = "기본 플레이스 여부. true이면 삭제 불가", example = "false")
    boolean isDefault,
    @Schema(description = "이모지 아이콘. 없으면 null", nullable = true)
    String icon,
    @Schema(description = "포함된 장소 수", example = "8")
    int placeCount,
    @Schema(description = "공개 범위", example = "private", allowableValues = {"public", "private"})
    String visibility
) {
    public static CollectionItem from(CollectionSummary summary) {
        Collection c = summary.collection();
        return new CollectionItem(
                c.getId(),
                c.getName(),
                c.isDefault(),
                c.getIcon(),
                summary.placeCount(),
                c.getVisibility()
        );
    }
}
