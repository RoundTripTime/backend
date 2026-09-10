package roundtrip.common.infrastructure;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import roundtrip.common.observability.AiProviderMetrics;
import roundtrip.common.observability.AiProviderResult;
import roundtrip.loadtest.MockFeatherlessProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AiConcurrencySaturationTest {

    private static final Duration MOCK_LATENCY = Duration.ofMillis(80);
    private static final long ACQUIRE_TIMEOUT_SECONDS = 10;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static RedissonClient redisson;

    private SimpleMeterRegistry registry;
    private AiProviderMetrics metrics;
    private FeatherlessAiRateLimiter limiter;
    private MockFeatherlessProvider mock;

    @BeforeAll
    static void openRedis() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        redisson = Redisson.create(config);
    }

    @AfterAll
    static void closeRedis() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        redisson.getKeys().flushdb();
        registry = new SimpleMeterRegistry();
        metrics = new AiProviderMetrics(registry);
        limiter = new FeatherlessAiRateLimiter(redisson, metrics);
        mock = new MockFeatherlessProvider(MockFeatherlessProvider.CAPACITY, MOCK_LATENCY);
    }

    @Test
    void belowProviderCapacity_callsSucceedWithLittleSemaphoreWait() throws Exception {
        RunResult result = runLoad(2, 4);

        assertThat(result.successes).isEqualTo(8);
        assertThat(result.acquireTimeouts).isZero();
        assertThat(result.providerOverCapacity).isZero();
        assertThat(result.maxInFlight).isLessThanOrEqualTo(2);
        assertThat(meanWaitMillis()).isLessThan(100);
    }

    @Test
    void aboveProviderCapacity_semaphoreKeepsMockAtMostFourConcurrentCalls() throws Exception {
        RunResult result = runLoad(12, 4);

        assertThat(result.successes).isEqualTo(48);
        assertThat(result.acquireTimeouts).isZero();
        assertThat(result.providerOverCapacity).isZero();
        assertThat(result.maxInFlight).isEqualTo(MockFeatherlessProvider.CAPACITY);
        assertThat(meanWaitMillis()).isGreaterThan(40);
        assertThat(registry.counter(
                "ai.provider.requests",
                "provider", "featherless",
                "operation", "place_extraction",
                "result", AiProviderResult.SUCCESS.label()).count()).isEqualTo(48);
    }

    private RunResult runLoad(int vus, int requestsPerVu) throws Exception {
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger acquireTimeouts = new AtomicInteger();
        AtomicInteger providerErrors = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(vus)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < vus; i++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    for (int n = 0; n < requestsPerVu; n++) {
                        oneCall(successes, acquireTimeouts, providerErrors);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        }
        return new RunResult(
                successes.get(),
                acquireTimeouts.get(),
                mock.overCapacity() + providerErrors.get(),
                mock.maxInFlight()
        );
    }

    private void oneCall(AtomicInteger successes, AtomicInteger acquireTimeouts, AtomicInteger providerErrors) {
        if (!limiter.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            metrics.recordRequest("featherless", "place_extraction", AiProviderResult.FALLBACK);
            acquireTimeouts.incrementAndGet();
            return;
        }
        try {
            metrics.recordCall("featherless", "place_extraction", () -> {
                mock.call();
                return Boolean.TRUE;
            });
            successes.incrementAndGet();
        } catch (RuntimeException e) {
            providerErrors.incrementAndGet();
        } finally {
            limiter.release();
        }
    }

    private double meanWaitMillis() {
        Timer timer = registry.timer(
                "ai.concurrency.wait.duration",
                "provider", "featherless",
                "result", "acquired");
        return timer.mean(TimeUnit.MILLISECONDS);
    }

    private record RunResult(int successes, int acquireTimeouts, int providerOverCapacity, int maxInFlight) {
    }
}
