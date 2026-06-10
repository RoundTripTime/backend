package roundtrip.notification.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.notification.domain.entity.DeviceToken;
import roundtrip.notification.domain.repository.DeviceTokenRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeviceTokenRepositoryImpl implements DeviceTokenRepository {

    private final DeviceTokenJpaRepository jpa;

    @Override
    public DeviceToken save(DeviceToken deviceToken) {
        return jpa.save(deviceToken);
    }

    @Override
    public Optional<DeviceToken> findByToken(String token) {
        return jpa.findByToken(token);
    }

    @Override
    public List<DeviceToken> findByUserId(UUID userId) {
        return jpa.findByUserId(userId);
    }

    @Override
    public void deleteByToken(String token) {
        jpa.deleteByToken(token);
    }

    @Override
    public void deleteByTokenIn(List<String> tokens) {
        jpa.deleteByTokenIn(tokens);
    }
}
