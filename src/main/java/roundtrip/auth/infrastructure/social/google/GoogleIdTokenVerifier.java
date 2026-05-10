package roundtrip.auth.infrastructure.social.google;

import org.springframework.stereotype.Component;
import roundtrip.auth.infrastructure.social.AbstractSocialIdTokenVerifier;
import roundtrip.auth.infrastructure.social.JwksKeyResolver;
import roundtrip.auth.infrastructure.social.SocialAuthProperties;
import roundtrip.user.domain.entity.SocialProvider;

@Component
public class GoogleIdTokenVerifier extends AbstractSocialIdTokenVerifier {

    private static final String ISS_WITH_SCHEME = "https://accounts.google.com";
    private static final String ISS_WITHOUT_SCHEME = "accounts.google.com";

    private final SocialAuthProperties.ProviderConfig config;

    public GoogleIdTokenVerifier(JwksKeyResolver jwksKeyResolver, SocialAuthProperties properties) {
        super(jwksKeyResolver);
        this.config = properties.google();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
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
        return ISS_WITH_SCHEME.equals(issuer) || ISS_WITHOUT_SCHEME.equals(issuer);
    }
}
