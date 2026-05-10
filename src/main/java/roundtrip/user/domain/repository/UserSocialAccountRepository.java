package roundtrip.user.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.user.domain.entity.UserSocialAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, UUID> {

    Optional<UserSocialAccount> findByProviderAndSocialId(String provider, String socialId);
}
