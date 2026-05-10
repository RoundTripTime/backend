package roundtrip.auth.infrastructure.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    long accessExpiryMs,
    long refreshExpiryMs
) {
}
