package roundtrip.user.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.user.domain.entity.SocialProvider;
import roundtrip.user.domain.entity.UserSocialAccount;

import java.util.Optional;
import java.util.UUID;

interface UserSocialAccountJpaRepository extends JpaRepository<UserSocialAccount, UUID> {

    Optional<UserSocialAccount> findByProviderAndSocialId(SocialProvider provider, String socialId);
}
