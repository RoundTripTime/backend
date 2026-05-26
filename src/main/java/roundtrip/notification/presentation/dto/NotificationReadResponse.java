package roundtrip.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "알림 읽음 처리 응답")
public record NotificationReadResponse(
    @Schema(description = "읽음 처리된 알림 ID") UUID notificationId,
    @Schema(description = "읽음 여부 (true 반환)") boolean isRead
) {}
