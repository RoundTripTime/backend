package roundtrip.common.observability;

import org.redisson.api.RedissonClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedissonClient redissonClient;

    public RedisHealthIndicator(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Health health() {
        try {
            redissonClient.getBucket("roundtrip:health:ping").isExists();
            return Health.up().build();
        } catch (RuntimeException ex) {
            return Health.down().build();
        }
    }
}
