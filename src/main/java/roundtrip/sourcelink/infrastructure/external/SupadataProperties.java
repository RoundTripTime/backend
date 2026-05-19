package roundtrip.sourcelink.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supadata")
public record SupadataProperties(
        String apiKey,
        String baseUrl
) {
}
