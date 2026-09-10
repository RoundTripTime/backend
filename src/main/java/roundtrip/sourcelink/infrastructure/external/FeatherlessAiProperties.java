package roundtrip.sourcelink.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "featherlessai")
public record FeatherlessAiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration acquireTimeout,
        Retry retry,
        CircuitBreaker circuitBreaker,
        RateLimit rateLimit
) {

    public FeatherlessAiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.featherless.ai/v1";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
        if (acquireTimeout == null) {
            acquireTimeout = Duration.ofSeconds(60);
        }
        if (retry == null) {
            retry = new Retry(3, Duration.ofMillis(200), 2.0, true);
        }
        if (circuitBreaker == null) {
            circuitBreaker = new CircuitBreaker(10, 5, 50.0f, Duration.ofSeconds(30), 2);
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(true, Duration.ZERO);
        }
    }

    public static FeatherlessAiProperties of(String apiKey, String model) {
        return new FeatherlessAiProperties(apiKey, model, null, null, null, null, null, null, null);
    }

    public record Retry(
            int maxAttempts,
            Duration waitDuration,
            double exponentialBackoffMultiplier,
            boolean retryOnTimeout
    ) {
    }

    public record CircuitBreaker(
            int slidingWindowSize,
            int minimumNumberOfCalls,
            float failureRateThreshold,
            Duration waitDurationInOpenState,
            int permittedNumberOfCallsInHalfOpenState
    ) {
    }

    public record RateLimit(
            boolean honorRetryAfter,
            Duration maxWaitForRetryAfter
    ) {
    }
}
