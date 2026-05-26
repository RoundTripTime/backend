package roundtrip.common.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

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

    private static final String SEMAPHORE_KEY = "featherlessai:concurrency";
    private static final int MAX_PERMITS = 4;

    private final RSemaphore semaphore;

    public FeatherlessAiRateLimiter(RedissonClient redissonClient) {
        this.semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
        this.semaphore.trySetPermits(MAX_PERMITS);
    }

    /**
     * permit을 획득한다. 타임아웃 내 실패 시 false 반환.
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        try {
            boolean acquired = semaphore.tryAcquire(Duration.ofMillis(unit.toMillis(timeout)));
            if (!acquired) {
                log.warn("FeatherlessAI concurrency limit reached, could not acquire permit within {} {}", timeout, unit);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * permit을 반환한다.
     */
    public void release() {
        semaphore.release();
    }
}
