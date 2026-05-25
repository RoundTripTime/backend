package roundtrip.user.domain.repository;

import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.vo.Email;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    User save(User user);

    void delete(User user);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
