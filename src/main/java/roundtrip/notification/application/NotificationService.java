package roundtrip.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.notification.domain.entity.Notification;
import roundtrip.notification.domain.repository.NotificationRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

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

    @Transactional
    public Notification markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markAsRead();
        return notificationRepository.save(notification);
    }

    public record NotificationListResult(List<Notification> notifications, UUID nextCursor) {}
}
