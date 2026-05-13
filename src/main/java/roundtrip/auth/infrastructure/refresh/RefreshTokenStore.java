package roundtrip.auth.infrastructure.refresh;

import java.time.Duration;
import java.util.UUID;

public interface RefreshTokenStore {

    void save(UUID userId, String jti, Duration ttl);

    boolean exists(UUID userId, String jti);

    void delete(UUID userId, String jti);

    void deleteAll(UUID userId);
}
