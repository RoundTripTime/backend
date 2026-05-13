package roundtrip.auth.infrastructure.social;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.common.exception.BusinessException;
import roundtrip.user.domain.entity.SocialProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SocialIdTokenVerifierRegistry {

    private final Map<SocialProvider, SocialIdTokenVerifier> byProvider;

    public SocialIdTokenVerifierRegistry(List<SocialIdTokenVerifier> verifiers) {
        this.byProvider = verifiers.stream()
            .collect(Collectors.toUnmodifiableMap(SocialIdTokenVerifier::provider, Function.identity()));
    }

    public SocialIdentity verify(SocialProvider provider, String idToken) {
        SocialIdTokenVerifier verifier = byProvider.get(provider);
        if (verifier == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER",
                "지원하지 않는 소셜 provider 입니다: " + provider);
        }
        return verifier.verify(idToken);
    }
}
