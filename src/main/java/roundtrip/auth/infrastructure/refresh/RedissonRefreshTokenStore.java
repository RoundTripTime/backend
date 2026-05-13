package roundtrip.auth.infrastructure.refresh;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedissonRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";
    private static final String DELETE_PATTERN = KEY_PREFIX + "%s:*";

    private final RedissonClient redisson;

    @Override
    public void save(UUID userId, String jti, Duration ttl) {
        RBucket<String> bucket = redisson.getBucket(key(userId, jti));
        bucket.set("1", ttl);
    }

    @Override
    public boolean exists(UUID userId, String jti) {
        return redisson.getBucket(key(userId, jti)).isExists();
    }

    @Override
    public void delete(UUID userId, String jti) {
        redisson.getBucket(key(userId, jti)).delete();
    }

    @Override
    public void deleteAll(UUID userId) {
        redisson.getKeys().deleteByPattern(DELETE_PATTERN.formatted(userId));
    }

    private String key(UUID userId, String jti) {
        return KEY_PREFIX + userId + ":" + jti;
    }
}
