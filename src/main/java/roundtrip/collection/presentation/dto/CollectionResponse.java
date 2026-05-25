package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.collection.domain.entity.Collection;

import java.util.UUID;

@Schema(description = "플레이스 응답")
public record CollectionResponse(
    @Schema(description = "플레이스 고유 ID")
    UUID collectionId,
    @Schema(description = "플레이스 이름", example = "일본")
    String name,
    @Schema(description = "기본 플레이스 여부", example = "false")
    boolean isDefault,
    @Schema(description = "이모지 아이콘. 없으면 null", nullable = true)
    String icon,
    @Schema(description = "공개 범위", example = "private", allowableValues = {"public", "private"})
    String visibility
) {
    public static CollectionResponse from(Collection c) {
        return new CollectionResponse(
                c.getId(),
                c.getName(),
                c.isDefault(),
                c.getIcon(),
                c.getVisibility()
        );
    }
}
