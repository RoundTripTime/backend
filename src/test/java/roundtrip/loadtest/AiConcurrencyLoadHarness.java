package roundtrip.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import roundtrip.common.infrastructure.FeatherlessAiRateLimiter;
import roundtrip.common.observability.AiProviderMetrics;
import roundtrip.common.observability.AiProviderResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redis 세마포어와 목 Provider만으로 부하 실험 HTTP를 연다.
 * 기본 PR CI에서는 쓰지 않는다. 실제 FeatherlessAI는 호출하지 않는다.
 */
public final class AiConcurrencyLoadHarness {

    public static void main(String[] args) throws Exception {
        String redisHost = env("REDIS_HOST", "127.0.0.1");
        int redisPort = Integer.parseInt(env("REDIS_PORT", "6379"));
        int harnessPort = Integer.parseInt(env("HARNESS_PORT", "18080"));
        long latencyMs = Long.parseLong(env("MOCK_LATENCY_MS", "500"));
        int capacity = Integer.parseInt(env("MOCK_CAPACITY", "4"));
        long acquireTimeoutSeconds = Long.parseLong(env("ACQUIRE_TIMEOUT_SECONDS", "10"));

        Config redisConfig = new Config();
        redisConfig.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        RedissonClient redisson = Redisson.create(redisConfig);
        redisson.getKeys().flushdb();

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AiProviderMetrics metrics = new AiProviderMetrics(registry);
        FeatherlessAiRateLimiter limiter = new FeatherlessAiRateLimiter(redisson, metrics);
        MockFeatherlessProvider mock = new MockFeatherlessProvider(capacity, Duration.ofMillis(latencyMs));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", harnessPort), 0);
        server.createContext("/actuator/health", exchange -> respond(exchange, 200, "text/plain", "ok"));
        server.createContext("/actuator/prometheus", exchange -> respond(
                exchange, 200, "text/plain; version=0.0.4; charset=utf-8", registry.scrape()));
        server.createContext("/internal/ai-loadtest/stats", exchange -> respond(
                exchange, 200, "application/json", statsJson(mock)));
        server.createContext("/internal/ai-loadtest/reset", exchange -> {
            mock.resetStats();
            respond(exchange, 200, "application/json", "{\"reset\":true}");
        });
        server.createContext("/internal/ai-loadtest/call", exchange ->
                handleCall(exchange, limiter, metrics, mock, acquireTimeoutSeconds));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.printf(
                "ai-concurrency harness listening on 127.0.0.1:%d latencyMs=%d capacity=%d acquireTimeoutSeconds=%d%n",
                harnessPort, latencyMs, capacity, acquireTimeoutSeconds);
        Runtime.getRuntime().addShutdownHook(new Thread(redisson::shutdown));
        Thread.currentThread().join();
    }

    private static void handleCall(
            HttpExchange exchange,
            FeatherlessAiRateLimiter limiter,
            AiProviderMetrics metrics,
            MockFeatherlessProvider mock,
            long acquireTimeoutSeconds
    ) throws IOException {
        if (!limiter.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS)) {
            metrics.recordRequest("featherless", "place_extraction", AiProviderResult.FALLBACK);
            respond(exchange, 503, "application/json", "{\"result\":\"acquire_timeout\"}");
            return;
        }
        try {
            metrics.recordCall("featherless", "place_extraction", () -> {
                mock.call();
                return Boolean.TRUE;
            });
            respond(exchange, 200, "application/json", "{\"result\":\"success\"}");
        } catch (MockFeatherlessProvider.OverCapacityException e) {
            respond(exchange, 429, "application/json", "{\"result\":\"provider_over_capacity\"}");
        } catch (RuntimeException e) {
            respond(exchange, 500, "application/json", "{\"result\":\"unknown_error\"}");
        } finally {
            limiter.release();
        }
    }

    private static String statsJson(MockFeatherlessProvider mock) {
        return String.format(Locale.US,
                "{\"max_in_flight\":%d,\"over_capacity\":%d,\"capacity\":%d,\"latency_ms\":%d}",
                mock.maxInFlight(), mock.overCapacity(), mock.capacity(), mock.latencyMs());
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
