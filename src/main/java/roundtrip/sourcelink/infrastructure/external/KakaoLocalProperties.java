package roundtrip.sourcelink.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao.local")
public record KakaoLocalProperties(
        String apiKey,
        String baseUrl
) {
}
