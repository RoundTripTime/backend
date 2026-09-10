package roundtrip.common.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import roundtrip.common.observability.AiProviderMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * FeatherlessAI API 동시 접속 제한기.
 * Premium 플랜 기준 최대 4 concurrent connections.
 * 추출 잡(background)과 Agent(foreground)가 공유한다.
 */
@Slf4j
@Component
public class FeatherlessAiRateLimiter {

    public static final String PROVIDER = "featherless";
    static final String SEMAPHORE_KEY = "featherlessai:concurrency";
    public static final int MAX_PERMITS = 4;

    private final RSemaphore semaphore;
    private final AiProviderMetrics metrics;

    public FeatherlessAiRateLimiter(RedissonClient redissonClient, AiProviderMetrics metrics) {
        this.metrics = metrics;
        this.semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
        this.semaphore.trySetPermits(MAX_PERMITS);
        this.metrics.registerConcurrencyGauges(PROVIDER, this::availablePermits, MAX_PERMITS);
    }

    /**
     * permit을 획득한다. 타임아웃 내 실패 시 false 반환.
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        long started = System.nanoTime();
        String result = "timeout";
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(Duration.ofMillis(unit.toMillis(timeout)));
            result = acquired ? "acquired" : "timeout";
            if (!acquired) {
                metrics.recordAcquireFailure(PROVIDER);
                log.warn("provider={} operation=acquire result=timeout timeout={} {}",
                        PROVIDER, timeout, unit);
            }
            return acquired;
        } catch (InterruptedException e) {
            result = "interrupted";
            metrics.recordAcquireFailure(PROVIDER);
            Thread.currentThread().interrupt();
            log.warn("provider={} operation=acquire result=interrupted", PROVIDER);
            return false;
        } finally {
            metrics.recordConcurrencyWait(PROVIDER, result, Duration.ofNanos(System.nanoTime() - started));
        }
    }

    /**
     * permit을 반환한다.
     */
    public void release() {
        semaphore.release();
    }

    int availablePermits() {
        return semaphore.availablePermits();
    }
}
