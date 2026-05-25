package roundtrip.user.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.vo.Email;

import java.util.Optional;
import java.util.UUID;

interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
