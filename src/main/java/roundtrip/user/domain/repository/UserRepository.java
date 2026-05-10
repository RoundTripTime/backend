package roundtrip.user.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.user.domain.entity.User;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
