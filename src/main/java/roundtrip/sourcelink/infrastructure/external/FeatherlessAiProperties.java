package roundtrip.sourcelink.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "featherlessai")
public record FeatherlessAiProperties(
        String apiKey,
        String model
) {
}
