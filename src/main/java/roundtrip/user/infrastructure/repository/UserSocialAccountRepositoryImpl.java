package roundtrip.user.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.user.domain.entity.UserSocialAccount;
import roundtrip.user.domain.repository.UserSocialAccountRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserSocialAccountRepositoryImpl implements UserSocialAccountRepository {

    private final UserSocialAccountJpaRepository jpa;

    @Override
    public Optional<UserSocialAccount> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public UserSocialAccount save(UserSocialAccount account) {
        return jpa.save(account);
    }

    @Override
    public void delete(UserSocialAccount account) {
        jpa.delete(account);
    }

    @Override
    public Optional<UserSocialAccount> findByProviderAndSocialId(String provider, String socialId) {
        return jpa.findByProviderAndSocialId(provider, socialId);
    }
}
