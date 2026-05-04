package roundtrip.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.user.domain.User;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
