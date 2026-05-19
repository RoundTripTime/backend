package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플레이스 수정 요청 (변경할 항목만 포함)")
public record UpdateCollectionRequest(
    @Schema(description = "플레이스 이름", example = "일본", nullable = true)
    String name,
    @Schema(description = "이모지 아이콘", nullable = true)
    String icon,
    @Schema(description = "공개 범위", example = "public", allowableValues = {"public", "private"}, nullable = true)
    String visibility
) {}
