package roundtrip.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterDeviceTokenRequest(
        @Schema(description = "FCM 디바이스 등록 토큰", example = "fGx...token")
        @NotBlank
        String token,

        @Schema(description = "디바이스 플랫폼", example = "android", allowableValues = {"ios", "android", "web"})
        @NotBlank
        @Pattern(regexp = "(?i)ios|android|web", message = "platform은 ios, android, web 중 하나여야 합니다.")
        String platform
) {
}
