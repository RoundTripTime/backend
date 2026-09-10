package roundtrip.common.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiProperties;

import java.net.http.HttpClient;

@Configuration
public class FeatherlessAiClientConfig {

    @Bean
    RestClient featherlessAiRestClient(FeatherlessAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        requestFactory.enableCompression(false);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    CircuitBreaker featherlessCircuitBreaker(FeatherlessAiProperties properties) {
        FeatherlessAiProperties.CircuitBreaker settings = properties.circuitBreaker();
        return CircuitBreaker.of("featherless", CircuitBreakerConfig.custom()
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(FeatherlessAiFailurePolicy::isCircuitFailure)
                .build());
    }

    @Bean
    Retry featherlessRetry(FeatherlessAiProperties properties) {
        FeatherlessAiProperties.Retry settings = properties.retry();
        long waitMs = settings.waitDuration() == null ? 1L : Math.max(1L, settings.waitDuration().toMillis());
        return Retry.of("featherless", RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        waitMs,
                        settings.exponentialBackoffMultiplier() <= 0 ? 1.0 : settings.exponentialBackoffMultiplier()))
                .retryOnException(error -> FeatherlessAiFailurePolicy.isRetryable(error, settings.retryOnTimeout()))
                .failAfterMaxAttempts(true)
                .build());
    }
}
