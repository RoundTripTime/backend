package roundtrip.notification.domain.repository;

import roundtrip.notification.domain.entity.DeviceToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository {

    DeviceToken save(DeviceToken deviceToken);

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(UUID userId);

    void deleteByToken(String token);

    void deleteByTokenIn(List<String> tokens);
}
