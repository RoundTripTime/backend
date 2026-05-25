package roundtrip.auth.infrastructure.social;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;

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
            throw new BusinessException(ErrorCode.PROVIDER_NOT_CONFIGURED, "provider=" + provider());
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                .keyLocator(header -> jwksKeyResolver.resolve(jwksUri(), (String) header.get("kid")))
                .build()
                .parseSignedClaims(idToken)
                .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "검증 실패: " + ex.getMessage());
        }

        if (!isValidIssuer(claims.getIssuer())) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "iss=" + claims.getIssuer());
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "aud=" + claims.getAudience());
        }

        return new SocialIdentity(provider(), claims.getSubject(), claims.get("email", String.class));
    }
}
