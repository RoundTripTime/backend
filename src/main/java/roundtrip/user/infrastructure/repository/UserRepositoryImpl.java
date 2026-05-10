package roundtrip.user.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.vo.Email;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpa;

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    @Override
    public void delete(User user) {
        jpa.delete(user);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email);
    }
}
