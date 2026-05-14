package roundtrip.auth.infrastructure.social.local;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.auth.infrastructure.social.SocialIdTokenVerifier;
import roundtrip.user.domain.entity.SocialProvider;

@Configuration
@Profile("local")
public class LocalSocialIdTokenVerifierConfig {

    @Bean
    public SocialIdTokenVerifier localKakaoVerifier() {
        return fake(SocialProvider.KAKAO);
    }

    @Bean
    public SocialIdTokenVerifier localGoogleVerifier() {
        return fake(SocialProvider.GOOGLE);
    }

    private SocialIdTokenVerifier fake(SocialProvider provider) {
        return new SocialIdTokenVerifier() {
            @Override
            public SocialProvider provider() {
                return provider;
            }

            @Override
            public SocialIdentity verify(String idToken) {
                String socialId = provider.name().toLowerCase() + "-" + idToken;
                String email = idToken + "@local.test";
                return new SocialIdentity(provider, socialId, email);
            }
        };
    }
}
