package roundtrip.common.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import roundtrip.common.observability.AiProviderMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatherlessAiRateLimiterTest {

    @Mock RedissonClient redissonClient;
    @Mock RSemaphore semaphore;

    private SimpleMeterRegistry registry;
    private FeatherlessAiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        when(redissonClient.getSemaphore("featherlessai:concurrency")).thenReturn(semaphore);
        limiter = new FeatherlessAiRateLimiter(redissonClient, new AiProviderMetrics(registry));
    }

    @Test
    void recordsWaitAsAcquiredWhenPermitIsGranted() throws InterruptedException {
        when(semaphore.tryAcquire(any(Duration.class))).thenReturn(true);

        assertThat(limiter.tryAcquire(1, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.timer(
                "ai.concurrency.wait.duration",
                "provider", "featherless",
                "result", "acquired").count()).isEqualTo(1);
        assertThat(registry.find("ai.concurrency.acquire.failures").counter()).isNull();
    }

    @Test
    void recordsTimeoutAndAcquireFailureWhenPermitIsNotGranted() throws InterruptedException {
        when(semaphore.tryAcquire(any(Duration.class))).thenReturn(false);

        assertThat(limiter.tryAcquire(10, TimeUnit.MILLISECONDS)).isFalse();

        assertThat(registry.timer(
                "ai.concurrency.wait.duration",
                "provider", "featherless",
                "result", "timeout").count()).isEqualTo(1);
        assertThat(registry.counter(
                "ai.concurrency.acquire.failures",
                "provider", "featherless").count()).isEqualTo(1);
    }

    @Test
    void recordsInterruptedWhenAcquireIsInterrupted() throws InterruptedException {
        when(semaphore.tryAcquire(any(Duration.class))).thenThrow(new InterruptedException("stopped"));

        assertThat(limiter.tryAcquire(1, TimeUnit.SECONDS)).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();

        assertThat(registry.timer(
                "ai.concurrency.wait.duration",
                "provider", "featherless",
                "result", "interrupted").count()).isEqualTo(1);
        assertThat(registry.counter(
                "ai.concurrency.acquire.failures",
                "provider", "featherless").count()).isEqualTo(1);
    }

    @Test
    void usedGaugeTracksMaxMinusAvailablePermits() {
        when(semaphore.availablePermits()).thenReturn(1);

        assertThat(registry.find("ai.concurrency.available").tag("provider", "featherless").gauge().value())
                .isEqualTo(1.0);
        assertThat(registry.find("ai.concurrency.used").tag("provider", "featherless").gauge().value())
                .isEqualTo(3.0);
    }
}
