package roundtrip.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.notification.application.NotificationService.NotificationListResult;

import java.util.List;
import java.util.UUID;

@Schema(description = "알림 목록 응답")
public record NotificationListResponse(
    @Schema(description = "알림 목록") List<NotificationResponse> items,
    @Schema(description = "다음 페이지 커서. null이면 마지막 페이지", nullable = true) UUID nextCursor
) {
    public static NotificationListResponse from(NotificationListResult result) {
        List<NotificationResponse> items = result.notifications().stream()
            .map(NotificationResponse::from)
            .toList();
        return new NotificationListResponse(items, result.nextCursor());
    }
}
