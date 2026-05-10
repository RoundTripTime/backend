package roundtrip.auth.infrastructure.social;

import roundtrip.auth.domain.SocialIdentity;
import roundtrip.user.domain.entity.SocialProvider;

public interface SocialIdTokenVerifier {

    SocialProvider provider();

    SocialIdentity verify(String idToken);
}
