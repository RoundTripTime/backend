package roundtrip.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RegisterDeviceTokenResponse(
        @Schema(description = "등록된 디바이스 토큰 ID")
        UUID deviceTokenId
) {
}
