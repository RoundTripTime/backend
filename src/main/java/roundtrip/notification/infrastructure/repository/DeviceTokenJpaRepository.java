package roundtrip.notification.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.notification.domain.entity.DeviceToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DeviceTokenJpaRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(UUID userId);

    void deleteByToken(String token);

    void deleteByTokenIn(List<String> tokens);
}
