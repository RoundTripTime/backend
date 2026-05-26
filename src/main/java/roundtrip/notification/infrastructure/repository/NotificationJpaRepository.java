package roundtrip.notification.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.notification.domain.entity.Notification;

import java.util.Optional;
import java.util.UUID;

interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
}
