package roundtrip.sourcelink.infrastructure.external;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import roundtrip.common.infrastructure.FeatherlessAiFailurePolicy;
import roundtrip.common.infrastructure.FeatherlessAiIsolation;
import roundtrip.common.infrastructure.FeatherlessAiRateLimiter;
import roundtrip.common.observability.AiProviderMetrics;
import roundtrip.common.observability.AiProviderResult;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatherlessAiIsolationHttpTest {

    private static final String SUCCESS_BODY = """
            {"choices":[{"message":{"role":"assistant","content":"{\\"places\\":[{\\"name\\":\\"경복궁\\",\\"category\\":\\"attraction\\",\\"confidence\\":0.95,\\"evidence\\":\\"자막\\"}]}"}}]}
            """;

    private FakeChatCompletionsServer server;
    private FeatherlessAiRateLimiter limiter;
    private SimpleMeterRegistry registry;
    private FeatherlessAiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeChatCompletionsServer();
        limiter = mock(FeatherlessAiRateLimiter.class);
        when(limiter.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(true);
        registry = new SimpleMeterRegistry();
        client = newClient(server.baseUrl(), Duration.ofMillis(200), wideCircuit());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void successReturnsPlacesWithoutChangingContract() {
        server.stub(exchange -> respond(exchange, 200, SUCCESS_BODY, Map.of("Content-Type", "application/json")));

        assertThat(client.parsePlaces("영상")).extracting(PlaceParseResult::name).containsExactly("경복궁");
        assertThat(server.postCount()).isEqualTo(1);
        verify(limiter).release();
        assertThat(requestCount(AiProviderResult.SUCCESS)).isEqualTo(1);
    }

    @Test
    void retries503ThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        server.stub(exchange -> {
            if (attempts.getAndIncrement() == 0) {
                respond(exchange, 503, "", Map.of());
                return;
            }
            respond(exchange, 200, SUCCESS_BODY, Map.of("Content-Type", "application/json"));
        });

        assertThat(client.parsePlaces("영상")).extracting(PlaceParseResult::name).containsExactly("경복궁");
        assertThat(server.postCount()).isEqualTo(2);
        verify(limiter).release();
    }

    @Test
    void doesNotRetry400() {
        server.stub(exchange -> respond(exchange, 400, "", Map.of()));

        assertThat(client.parsePlaces("영상")).isEmpty();
        assertThat(server.postCount()).isEqualTo(1);
        verify(limiter).release();
        assertThat(requestCount(AiProviderResult.CLIENT_ERROR)).isEqualTo(1);
    }

    @Test
    void rateLimitedDoesNotWaitRetryAfterAndDoesNotRetry() {
        server.stub(exchange -> respond(exchange, 429, "", Map.of("Retry-After", "120")));

        long started = System.nanoTime();
        assertThat(client.parsePlaces("영상")).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(server.postCount()).isEqualTo(1);
        verify(limiter).release();
        assertThat(requestCount(AiProviderResult.RATE_LIMITED)).isEqualTo(1);
    }

    @Test
    void serverErrorIsRetriedThenFallsBackToEmptyAndReleasesPermit() {
        server.stub(exchange -> respond(exchange, 500, "", Map.of()));

        assertThat(client.parsePlaces("영상")).isEmpty();
        assertThat(server.postCount()).isEqualTo(3);
        verify(limiter).release();
        assertThat(requestCount(AiProviderResult.SERVER_ERROR)).isEqualTo(3);
    }

    @Test
    void timeoutDoesNotHangAndRecordsTimeout() {
        server.stub(exchange -> {
            sleep(Duration.ofMillis(800));
            respond(exchange, 200, SUCCESS_BODY, Map.of("Content-Type", "application/json"));
        });
        client = newClient(server.baseUrl(), Duration.ofMillis(80), wideCircuit());

        long started = System.nanoTime();
        assertThat(client.parsePlaces("영상")).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        verify(limiter).release();
        assertThat(requestCount(AiProviderResult.TIMEOUT)).isPositive();
    }

    @Test
    void connectionFailureFallsBackAndReleasesPermit() {
        client = newClient("http://127.0.0.1:1", Duration.ofMillis(200), wideCircuit());

        assertThat(client.parsePlaces("영상")).isEmpty();
        verify(limiter).release();
        assertThat(requestCount(AiProviderResult.UNKNOWN_ERROR)
                + requestCount(AiProviderResult.TIMEOUT)).isPositive();
    }

    @Test
    void acquireTimeoutDoesNotCallProviderOrReleasePermit() {
        when(limiter.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(false);
        server.stub(exchange -> respond(exchange, 200, SUCCESS_BODY, Map.of("Content-Type", "application/json")));

        assertThat(client.parsePlaces("영상")).isEmpty();
        assertThat(server.postCount()).isEqualTo(0);
        verify(limiter, never()).release();
        assertThat(requestCount(AiProviderResult.FALLBACK)).isEqualTo(1);
    }

    @Test
    void openCircuitSkipsHttpAndSemaphore() {
        client = newClient(server.baseUrl(), Duration.ofMillis(200), new FeatherlessAiProperties.CircuitBreaker(
                2, 2, 50.0f, Duration.ofHours(1), 1));
        server.stub(exchange -> respond(exchange, 500, "", Map.of()));
        assertThat(client.parsePlaces("영상")).isEmpty();
        server.resetRequests();

        assertThat(client.parsePlaces("영상")).isEmpty();
        assertThat(server.postCount()).isEqualTo(0);
        verify(limiter, times(1)).tryAcquire(anyLong(), any(TimeUnit.class));
        verify(limiter, times(1)).release();
        assertThat(requestCount(AiProviderResult.CIRCUIT_OPEN)).isPositive();
    }

    private static FeatherlessAiProperties.CircuitBreaker wideCircuit() {
        return new FeatherlessAiProperties.CircuitBreaker(20, 20, 50.0f, Duration.ofHours(1), 1);
    }

    private FeatherlessAiClient newClient(
            String baseUrl,
            Duration readTimeout,
            FeatherlessAiProperties.CircuitBreaker circuitBreakerSettings
    ) {
        FeatherlessAiProperties properties = new FeatherlessAiProperties(
                "test-key",
                "test-model",
                baseUrl,
                Duration.ofMillis(200),
                readTimeout,
                Duration.ofMillis(50),
                new FeatherlessAiProperties.Retry(3, Duration.ofMillis(1), 1.0, true),
                circuitBreakerSettings,
                new FeatherlessAiProperties.RateLimit(true, Duration.ZERO)
        );
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        requestFactory.enableCompression(false);
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", "application/json")
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("featherless-test-" + System.nanoTime(), CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.circuitBreaker().slidingWindowSize())
                .minimumNumberOfCalls(properties.circuitBreaker().minimumNumberOfCalls())
                .failureRateThreshold(properties.circuitBreaker().failureRateThreshold())
                .waitDurationInOpenState(properties.circuitBreaker().waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.circuitBreaker().permittedNumberOfCallsInHalfOpenState())
                .recordException(FeatherlessAiFailurePolicy::isCircuitFailure)
                .build());
        long waitMs = Math.max(1L, properties.retry().waitDuration().toMillis());
        Retry retry = Retry.of("featherless-test-" + System.nanoTime(), RetryConfig.custom()
                .maxAttempts(properties.retry().maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        waitMs,
                        properties.retry().exponentialBackoffMultiplier()))
                .retryOnException(error -> FeatherlessAiFailurePolicy.isRetryable(error, properties.retry().retryOnTimeout()))
                .failAfterMaxAttempts(true)
                .build());
        FeatherlessAiIsolation isolation = new FeatherlessAiIsolation(
                circuitBreaker,
                retry,
                limiter,
                new AiProviderMetrics(registry),
                properties
        );
        return new FeatherlessAiClient(properties, JsonMapper.builder().build(), restClient, isolation);
    }

    private double requestCount(AiProviderResult result) {
        var counter = registry.find("ai.provider.requests")
                .tag("provider", "featherless")
                .tag("operation", "place_extraction")
                .tag("result", result.label())
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private static void respond(HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws IOException {
        headers.forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            if (bytes.length > 0) {
                output.write(bytes);
            }
        }
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class FakeChatCompletionsServer {

        private final HttpServer httpServer;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final AtomicInteger postCount = new AtomicInteger();
        private volatile HttpHandler handler = exchange -> respond(exchange, 500, "", Map.of());

        private FakeChatCompletionsServer() throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpServer.createContext("/chat/completions", exchange -> {
                try {
                    exchange.getRequestBody().readAllBytes();
                    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        respond(exchange, 405, "", Map.of());
                        return;
                    }
                    postCount.incrementAndGet();
                    handler.handle(exchange);
                } finally {
                    exchange.close();
                }
            });
            httpServer.setExecutor(executor);
            httpServer.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + httpServer.getAddress().getPort();
        }

        private int postCount() {
            return postCount.get();
        }

        private void resetRequests() {
            postCount.set(0);
        }

        private void stub(HttpHandler next) {
            this.handler = next;
        }

        private void stop() {
            httpServer.stop(0);
            executor.shutdownNow();
        }
    }
}
