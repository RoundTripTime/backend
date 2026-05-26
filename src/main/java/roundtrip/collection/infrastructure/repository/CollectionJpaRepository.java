package roundtrip.collection.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.collection.domain.entity.Collection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CollectionJpaRepository extends JpaRepository<Collection, UUID> {

    List<Collection> findByUserId(UUID userId);

    Optional<Collection> findByIdAndUserId(UUID id, UUID userId);

    Optional<Collection> findByShareToken(String shareToken);
}
