package roundtrip.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.notification.domain.entity.Notification;
import roundtrip.notification.domain.entity.NotificationType;
import roundtrip.notification.domain.repository.NotificationRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    static final String REVIEW_REMINDER_MESSAGE = "추출한 장소를 확인해주세요!";

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public NotificationListResult getNotifications(UUID userId, Boolean isRead, UUID cursorId, int limit) {
        List<Notification> notifications = notificationRepository.findByUserIdBefore(
            userId, isRead, cursorId, limit + 1);

        boolean hasMore = notifications.size() > limit;
        if (hasMore) {
            notifications = notifications.subList(0, limit);
        }
        UUID nextCursor = hasMore && !notifications.isEmpty() ? notifications.getLast().getId() : null;
        return new NotificationListResult(notifications, nextCursor);
    }

    /**
     * 추출 완료 후 사용자가 확인하지 않은 후보가 남아있을 때 보내는 리마인드 알림을 생성한다.
     * 생성 후 {@link NotificationCreatedEvent}를 발행하여 FCM 푸시를 트리거한다.
     */
    @Transactional
    public Notification createReviewReminder(UUID userId, UUID jobId) {
        return create(userId, NotificationType.EXTRACTION_REVIEW_REMINDER, jobId, REVIEW_REMINDER_MESSAGE);
    }

    @Transactional
    public Notification create(UUID userId, NotificationType type, UUID jobId, String message) {
        Notification notification = notificationRepository.save(
                Notification.create(userId, type.value(), jobId, message));
        eventPublisher.publishEvent(new NotificationCreatedEvent(userId, type, jobId, message));
        return notification;
    }

    @Transactional
    public Notification markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
        return notificationRepository.save(notification);
    }

    public record NotificationListResult(List<Notification> notifications, UUID nextCursor) {}
}
