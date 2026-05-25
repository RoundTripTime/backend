package roundtrip.auth.infrastructure.social;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.social")
public record SocialAuthProperties(ProviderConfig kakao, ProviderConfig google) {

    public record ProviderConfig(String clientId, String issuer, String jwksUri) {
    }
}
