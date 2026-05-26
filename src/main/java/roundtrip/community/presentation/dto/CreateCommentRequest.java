package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "댓글 작성 요청")
public record CreateCommentRequest(
    @Schema(description = "댓글 본문 (최대 300자)", example = "저도 가고 싶어요!")
    @NotBlank @Size(max = 300) String content
) {}
