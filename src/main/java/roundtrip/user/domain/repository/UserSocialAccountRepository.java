package roundtrip.user.domain.repository;

import roundtrip.user.domain.entity.SocialProvider;
import roundtrip.user.domain.entity.UserSocialAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserSocialAccountRepository {

    Optional<UserSocialAccount> findById(UUID id);

    UserSocialAccount save(UserSocialAccount account);

    void delete(UserSocialAccount account);

    Optional<UserSocialAccount> findByProviderAndSocialId(SocialProvider provider, String socialId);
}
