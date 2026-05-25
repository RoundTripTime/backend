package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "플레이스 생성 요청")
public record CreateCollectionRequest(
    @Schema(description = "플레이스 이름 (최대 100자)", example = "일본")
    @NotBlank @Size(max = 100) String name,
    @Schema(description = "이모지 아이콘", nullable = true)
    String icon
) {}
