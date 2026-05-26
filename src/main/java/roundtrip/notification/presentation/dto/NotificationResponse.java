package roundtrip.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.notification.domain.entity.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "알림 응답")
public record NotificationResponse(
    @Schema(description = "알림 고유 ID") UUID notificationId,
    @Schema(description = "알림 종류 (job_completed / job_failed)") String type,
    @Schema(description = "연결된 분석 잡 ID", nullable = true) UUID jobId,
    @Schema(description = "알림 표시 메시지") String message,
    @Schema(description = "읽음 여부") boolean isRead,
    @Schema(description = "알림 생성 일시") OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.getId(), n.getType(), n.getJobId(),
            n.getMessage(), n.isRead(), n.getCreatedAt()
        );
    }
}
