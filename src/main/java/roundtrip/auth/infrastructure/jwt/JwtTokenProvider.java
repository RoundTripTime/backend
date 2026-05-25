package roundtrip.auth.infrastructure.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import roundtrip.auth.domain.IssuedTokens;
import roundtrip.auth.domain.TokenType;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";

    private final SecretKey signingKey;
    private final Duration accessExpiry;
    private final Duration refreshExpiry;

    public JwtTokenProvider(JwtProperties properties) {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret은 최소 32바이트 이상이어야 합니다 (HS256)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpiry = Duration.ofMillis(properties.accessExpiryMs());
        this.refreshExpiry = Duration.ofMillis(properties.refreshExpiryMs());
    }

    public IssuedTokens issuePair(UUID userId) {
        Instant now = Instant.now();
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String accessToken = build(userId, TokenType.ACCESS, accessJti, now, accessExpiry);
        String refreshToken = build(userId, TokenType.REFRESH, refreshJti, now, refreshExpiry);

        return new IssuedTokens(accessToken, refreshToken, refreshJti);
    }

    public Claims parseAndValidate(String token, TokenType expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "파싱 실패: " + e.getMessage());
        }
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.name().equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN,
                "토큰 타입 불일치 expected=" + expectedType.name() + " actual=" + type);
        }
        return claims;
    }

    public Duration refreshExpiry() {
        return refreshExpiry;
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    private String build(UUID userId, TokenType type, String jti, Instant now, Duration ttl) {
        return Jwts.builder()
            .subject(userId.toString())
            .id(jti)
            .claim(CLAIM_TYPE, type.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }
}
