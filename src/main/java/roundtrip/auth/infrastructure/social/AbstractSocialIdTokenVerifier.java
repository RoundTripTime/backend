package roundtrip.auth.infrastructure.social;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.common.exception.BusinessException;

public abstract class AbstractSocialIdTokenVerifier implements SocialIdTokenVerifier {

    private final JwksKeyResolver jwksKeyResolver;

    protected AbstractSocialIdTokenVerifier(JwksKeyResolver jwksKeyResolver) {
        this.jwksKeyResolver = jwksKeyResolver;
    }

    protected abstract String jwksUri();

    protected abstract String expectedAudience();

    protected abstract boolean isValidIssuer(String issuer);

    @Override
    public final SocialIdentity verify(String idToken) {
        String audience = expectedAudience();
        if (audience == null || audience.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PROVIDER_NOT_CONFIGURED",
                provider() + " 로그인이 구성되지 않았습니다");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                .keyLocator(header -> jwksKeyResolver.resolve(jwksUri(), (String) header.get("kid")))
                .build()
                .parseSignedClaims(idToken)
                .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN",
                "id_token 검증 실패: " + ex.getMessage());
        }

        if (!isValidIssuer(claims.getIssuer())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN",
                "iss가 올바르지 않습니다");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN",
                "aud가 올바르지 않습니다");
        }

        return new SocialIdentity(provider(), claims.getSubject(), claims.get("email", String.class));
    }
}
