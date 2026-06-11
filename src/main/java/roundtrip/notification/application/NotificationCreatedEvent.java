package roundtrip.notification.application;

import roundtrip.notification.domain.entity.NotificationType;

import java.util.UUID;

/**
 * 알림이 생성되었을 때 발행되는 이벤트. FCM 푸시 전송 트리거로 사용된다.
 */
public record NotificationCreatedEvent(
        UUID userId,
        NotificationType type,
        UUID jobId,
        String message
) {
}
