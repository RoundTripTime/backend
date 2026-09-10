package roundtrip.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Slf4j
@Component
public class AiProviderMetrics {

    static final String REQUESTS = "ai.provider.requests";
    static final String REQUEST_DURATION = "ai.provider.request.duration";
    static final String CONCURRENCY_AVAILABLE = "ai.concurrency.available";
    static final String CONCURRENCY_USED = "ai.concurrency.used";
    static final String CONCURRENCY_WAIT = "ai.concurrency.wait.duration";
    static final String ACQUIRE_FAILURES = "ai.concurrency.acquire.failures";

    private final MeterRegistry registry;

    public AiProviderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public <T> T recordCall(String provider, String operation, Supplier<T> call) {
        Timer.Sample sample = startTimer();
        long started = System.nanoTime();
        AiProviderResult result = AiProviderResult.SUCCESS;
        try {
            return call.get();
        } catch (RuntimeException e) {
            result = classify(e);
            throw e;
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            recordLatency(provider, operation, sample);
            recordRequest(provider, operation, result);
            logOutcome(provider, operation, result, durationMs);
        }
    }

    public void recordRequest(String provider, String operation, AiProviderResult result) {
        Counter.builder(REQUESTS)
                .tag("provider", provider)
                .tag("operation", operation)
                .tag("result", result.label())
                .register(registry)
                .increment();
    }

    public void recordLatency(String provider, String operation, Timer.Sample sample) {
        sample.stop(requestTimer(provider, operation));
    }

    public void recordLatency(String provider, String operation, Duration duration) {
        requestTimer(provider, operation).record(duration);
    }

    public void recordConcurrencyWait(String provider, String result, Duration wait) {
        Timer.builder(CONCURRENCY_WAIT)
                .tag("provider", provider)
                .tag("result", result)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(wait);
    }

    public void recordAcquireFailure(String provider) {
        Counter.builder(ACQUIRE_FAILURES)
                .tag("provider", provider)
                .register(registry)
                .increment();
    }

    public void registerConcurrencyGauges(String provider, IntSupplier availablePermits, int maxPermits) {
        Gauge.builder(CONCURRENCY_AVAILABLE, availablePermits, supplier -> (double) supplier.getAsInt())
                .tag("provider", provider)
                .register(registry);
        Gauge.builder(CONCURRENCY_USED, availablePermits, supplier -> {
                    int available = supplier.getAsInt();
                    return (double) Math.max(0, maxPermits - available);
                })
                .tag("provider", provider)
                .register(registry);
    }

    public AiProviderResult classify(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return AiProviderResult.TIMEOUT;
            }
            if (current instanceof RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 429) {
                    return AiProviderResult.RATE_LIMITED;
                }
                if (status >= 500) {
                    return AiProviderResult.SERVER_ERROR;
                }
                if (status >= 400) {
                    return AiProviderResult.CLIENT_ERROR;
                }
            }
            if (current instanceof ResourceAccessException && isTimeoutMessage(current.getMessage())) {
                return AiProviderResult.TIMEOUT;
            }
            current = current.getCause();
        }
        return AiProviderResult.UNKNOWN_ERROR;
    }

    public void logOutcome(String provider, String operation, AiProviderResult result, long durationMs) {
        if (result == AiProviderResult.SUCCESS) {
            log.debug("provider={} operation={} result={} durationMs={}",
                    provider, operation, result.label(), durationMs);
            return;
        }
        log.warn("provider={} operation={} result={} durationMs={}",
                provider, operation, result.label(), durationMs);
    }

    private Timer requestTimer(String provider, String operation) {
        return Timer.builder(REQUEST_DURATION)
                .tag("provider", provider)
                .tag("operation", operation)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    private boolean isTimeoutMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timed out") || lower.contains("timeout");
    }
}
