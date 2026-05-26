package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "포스트 작성 요청")
public record CreatePostRequest(
    @Schema(description = "포스트 본문 (최대 1000자)", example = "도쿄 여행 다녀왔어요")
    @NotBlank @Size(max = 1000) String content,

    @Schema(description = "태그할 장소 ID 배열 (최대 10개)", nullable = true)
    @Size(max = 10) List<UUID> taggedPlaceIds,

    @Schema(description = "태그할 플랜 ID", nullable = true)
    UUID taggedItineraryId
) {}
