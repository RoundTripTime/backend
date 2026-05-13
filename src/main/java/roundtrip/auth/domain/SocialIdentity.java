package roundtrip.auth.domain;

import roundtrip.user.domain.entity.SocialProvider;

public record SocialIdentity(SocialProvider provider, String socialId, String email) {
}
