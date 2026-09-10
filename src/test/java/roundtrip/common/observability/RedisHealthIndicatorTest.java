package roundtrip.common.observability;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisHealthIndicatorTest {

    @Test
    void reportsUpWhenRedisRespondsAndDownWhenItDoesNot() {
        RedissonClient redisson = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<Object> bucket = mock(RBucket.class);
        when(redisson.getBucket(anyString())).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(false);

        RedisHealthIndicator indicator = new RedisHealthIndicator(redisson);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

        when(redisson.getBucket(anyString())).thenThrow(new RuntimeException("connection refused"));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails()).doesNotContainKey("error");
    }
}
