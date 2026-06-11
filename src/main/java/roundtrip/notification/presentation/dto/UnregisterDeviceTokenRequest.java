package roundtrip.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UnregisterDeviceTokenRequest(
        @Schema(description = "해제할 FCM 디바이스 등록 토큰", example = "fGx...token")
        @NotBlank
        String token
) {
}
