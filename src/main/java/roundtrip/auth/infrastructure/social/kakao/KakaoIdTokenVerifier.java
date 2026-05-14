package roundtrip.auth.infrastructure.social.kakao;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import roundtrip.auth.infrastructure.social.AbstractSocialIdTokenVerifier;
import roundtrip.auth.infrastructure.social.JwksKeyResolver;
import roundtrip.auth.infrastructure.social.SocialAuthProperties;
import roundtrip.user.domain.entity.SocialProvider;

@Component
@Profile("!local")
public class KakaoIdTokenVerifier extends AbstractSocialIdTokenVerifier {

    private final SocialAuthProperties.ProviderConfig config;

    public KakaoIdTokenVerifier(JwksKeyResolver jwksKeyResolver, SocialAuthProperties properties) {
        super(jwksKeyResolver);
        this.config = properties.kakao();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    protected String jwksUri() {
        return config.jwksUri();
    }

    @Override
    protected String expectedAudience() {
        return config.clientId();
    }

    @Override
    protected boolean isValidIssuer(String issuer) {
        return config.issuer().equals(issuer);
    }
}
