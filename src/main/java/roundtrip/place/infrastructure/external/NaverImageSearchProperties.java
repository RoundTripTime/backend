package roundtrip.place.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.image-search")
public record NaverImageSearchProperties(
        String clientId,
        String clientSecret,
        String baseUrl
) {
}
