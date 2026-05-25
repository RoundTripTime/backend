package roundtrip.sourcelink.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "링크 제출 요청")
public record SubmitSourceLinkRequest(
    @Schema(description = "공유 수신된 URL", example = "https://youtube.com/shorts/abc123")
    @NotBlank String url
) {
}
