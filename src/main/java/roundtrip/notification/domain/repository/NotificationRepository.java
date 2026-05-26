package roundtrip.notification.domain.repository;

import roundtrip.notification.domain.entity.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    List<Notification> findByUserIdBefore(UUID userId, Boolean isRead, UUID cursorId, int limit);
}
