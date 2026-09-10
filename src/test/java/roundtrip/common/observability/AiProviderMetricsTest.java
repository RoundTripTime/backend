package roundtrip.common.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderMetricsTest {

    private SimpleMeterRegistry registry;
    private AiProviderMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiProviderMetrics(registry);
    }

    @Test
    void recordsRequestCountWithBoundedResultLabel() {
        metrics.recordRequest("featherless", "place_extraction", AiProviderResult.SUCCESS);
        metrics.recordRequest("featherless", "place_extraction", AiProviderResult.TIMEOUT);

        assertThat(registry.counter(
                "ai.provider.requests",
                "provider", "featherless",
                "operation", "place_extraction",
                "result", "success").count()).isEqualTo(1);
        assertThat(registry.counter(
                "ai.provider.requests",
                "provider", "featherless",
                "operation", "place_extraction",
                "result", "timeout").count()).isEqualTo(1);
    }

    @Test
    void recordCall_successIncrementsCounterAndRethrowsClassifiedFailures() {
        String value = metrics.recordCall("kakao", "place_search", () -> "ok");
        assertThat(value).isEqualTo("ok");
        assertThat(registry.counter(
                "ai.provider.requests",
                "provider", "kakao",
                "operation", "place_search",
                "result", "success").count()).isEqualTo(1);

        try {
            metrics.recordCall("kakao", "place_search", () -> {
                throw responseException(503);
            });
        } catch (RestClientResponseException ignored) {
            // classified as server_error
        }
        assertThat(registry.counter(
                "ai.provider.requests",
                "provider", "kakao",
                "operation", "place_search",
                "result", "server_error").count()).isEqualTo(1);
    }

    @Test
    void recordsProviderLatency() {
        metrics.recordLatency("supadata", "extract", Duration.ofMillis(40));

        var timer = registry.timer("ai.provider.request.duration", "provider", "supadata", "operation", "extract");
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(40);
    }

    @Test
    void classifiesTimeoutAndHttpStatusWithoutUsingExceptionMessageAsLabel() {
        assertThat(metrics.classify(new SocketTimeoutException("read timed out")))
                .isEqualTo(AiProviderResult.TIMEOUT);
        assertThat(metrics.classify(responseException(429))).isEqualTo(AiProviderResult.RATE_LIMITED);
        assertThat(metrics.classify(responseException(503))).isEqualTo(AiProviderResult.SERVER_ERROR);
        assertThat(metrics.classify(responseException(400))).isEqualTo(AiProviderResult.CLIENT_ERROR);
        assertThat(metrics.classify(new ResourceAccessException("I/O error")))
                .isEqualTo(AiProviderResult.UNKNOWN_ERROR);
        assertThat(metrics.classify(new ResourceAccessException("connect timed out")))
                .isEqualTo(AiProviderResult.TIMEOUT);
        assertThat(metrics.classify(new IllegalStateException("boom")))
                .isEqualTo(AiProviderResult.UNKNOWN_ERROR);
    }

    @Test
    void recordsConcurrencyWaitAndAcquireFailure() {
        metrics.recordConcurrencyWait("featherless", "acquired", Duration.ofMillis(12));
        metrics.recordAcquireFailure("featherless");

        assertThat(registry.timer(
                "ai.concurrency.wait.duration",
                "provider", "featherless",
                "result", "acquired").count()).isEqualTo(1);
        assertThat(registry.counter(
                "ai.concurrency.acquire.failures",
                "provider", "featherless").count()).isEqualTo(1);
    }

    @Test
    void usedPermitsEqualMaxMinusAvailable() {
        metrics.registerConcurrencyGauges("featherless", () -> 1, 4);

        assertThat(registry.find("ai.concurrency.available")
                .tag("provider", "featherless").gauge().value()).isEqualTo(1.0);
        assertThat(registry.find("ai.concurrency.used")
                .tag("provider", "featherless").gauge().value()).isEqualTo(3.0);
    }

    private static RestClientResponseException responseException(int status) {
        return new RestClientResponseException(
                "status " + status,
                HttpStatusCode.valueOf(status),
                "reason",
                HttpHeaders.EMPTY,
                null,
                null);
    }
}
