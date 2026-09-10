package roundtrip.common.infrastructure;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import roundtrip.common.observability.AiProviderMetrics;
import roundtrip.common.observability.AiProviderResult;
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 서킷 확인 뒤 세마포어를 잡고 Provider를 호출한다.
 * 예외가 나도 permit은 반환한다. 429는 Retry-After를 읽되 호출 스레드를 오래 붙잡지 않는다.
 */
@Slf4j
@Component
public class FeatherlessAiIsolation {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final FeatherlessAiRateLimiter rateLimiter;
    private final AiProviderMetrics metrics;
    private final FeatherlessAiProperties properties;

    public FeatherlessAiIsolation(
            CircuitBreaker featherlessCircuitBreaker,
            Retry featherlessRetry,
            FeatherlessAiRateLimiter rateLimiter,
            AiProviderMetrics metrics,
            FeatherlessAiProperties properties
    ) {
        this.circuitBreaker = featherlessCircuitBreaker;
        this.retry = featherlessRetry;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
        this.properties = properties;
    }

    public <T> T run(
            String operation,
            Duration acquireTimeout,
            Supplier<T> work,
            Function<AiProviderResult, T> onDenied
    ) {
        if (isCircuitOpen()) {
            metrics.recordRequest(FeatherlessAiRateLimiter.PROVIDER, operation, AiProviderResult.CIRCUIT_OPEN);
            metrics.logOutcome(FeatherlessAiRateLimiter.PROVIDER, operation, AiProviderResult.CIRCUIT_OPEN, 0);
            return onDenied.apply(AiProviderResult.CIRCUIT_OPEN);
        }
        if (!rateLimiter.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            metrics.recordRequest(FeatherlessAiRateLimiter.PROVIDER, operation, AiProviderResult.FALLBACK);
            metrics.logOutcome(FeatherlessAiRateLimiter.PROVIDER, operation, AiProviderResult.FALLBACK, 0);
            return onDenied.apply(AiProviderResult.FALLBACK);
        }
        try {
            return work.get();
        } finally {
            rateLimiter.release();
        }
    }

    public <T> T invokeHttp(String operation, Supplier<T> http) {
        try {
            return retry.executeSupplier(() -> circuitBreaker.executeSupplier(() ->
                    metrics.recordCall(FeatherlessAiRateLimiter.PROVIDER, operation, () -> applyRateLimitPolicy(http))));
        } catch (CallNotPermittedException e) {
            metrics.recordRequest(FeatherlessAiRateLimiter.PROVIDER, operation, AiProviderResult.CIRCUIT_OPEN);
            metrics.logOutcome(FeatherlessAiRateLimiter.PROVIDER, operation, AiProviderResult.CIRCUIT_OPEN, 0);
            throw e;
        }
    }

    boolean isCircuitOpen() {
        CircuitBreaker.State state = circuitBreaker.getState();
        return state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN;
    }

    private <T> T applyRateLimitPolicy(Supplier<T> http) {
        try {
            return http.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 429) {
                throw e;
            }
            Duration retryAfter = parseRetryAfter(retryAfterHeader(e));
            log.warn("provider={} operation=http result=rate_limited retryAfter={}",
                    FeatherlessAiRateLimiter.PROVIDER, retryAfter);
            Duration maxWait = properties.rateLimit().maxWaitForRetryAfter();
            if (properties.rateLimit().honorRetryAfter()
                    && retryAfter != null
                    && maxWait != null
                    && !maxWait.isZero()
                    && !maxWait.isNegative()
                    && retryAfter.compareTo(maxWait) <= 0) {
                sleep(retryAfter);
            }
            throw e;
        }
    }

    public static Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String retryAfterHeader(RestClientResponseException e) {
        HttpHeaders headers = e.getResponseHeaders();
        if (headers == null) {
            return null;
        }
        return headers.getFirst("Retry-After");
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
