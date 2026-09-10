package roundtrip.loadtest;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FeatherlessAI 한도를 흉내 내는 목 Provider.
 * 지연과 동시 처리 한도를 고정해서 세마포어 효과를 측정한다.
 */
public final class MockFeatherlessProvider {

    public static final int CAPACITY = 4;
    public static final Duration DEFAULT_LATENCY = Duration.ofMillis(500);

    private final int capacity;
    private final Duration latency;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final AtomicInteger overCapacity = new AtomicInteger();

    public MockFeatherlessProvider(int capacity, Duration latency) {
        this.capacity = capacity;
        this.latency = latency;
    }

    public void call() {
        int current = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(current, Math::max);
        try {
            if (current > capacity) {
                overCapacity.incrementAndGet();
                throw new OverCapacityException(current, capacity);
            }
            sleep();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    public int overCapacity() {
        return overCapacity.get();
    }

    public int capacity() {
        return capacity;
    }

    public long latencyMs() {
        return latency.toMillis();
    }

    public void resetStats() {
        maxInFlight.set(0);
        overCapacity.set(0);
    }

    private void sleep() {
        try {
            Thread.sleep(latency.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mock provider interrupted", e);
        }
    }

    public static final class OverCapacityException extends RuntimeException {
        public OverCapacityException(int current, int capacity) {
            super("in-flight=" + current + " capacity=" + capacity);
        }
    }
}
