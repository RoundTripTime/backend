package roundtrip.credit.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "광고 시청 완료 요청")
public record AdSessionCompleteRequest(
    @Schema(description = "시청 시작 시 발급된 세션 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    UUID adSessionId
) {
}
