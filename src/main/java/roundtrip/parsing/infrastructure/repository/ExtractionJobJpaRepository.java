package roundtrip.parsing.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.parsing.domain.entity.ExtractionJob;

import java.util.Optional;
import java.util.UUID;

interface ExtractionJobJpaRepository extends JpaRepository<ExtractionJob, UUID> {

    Optional<ExtractionJob> findBySourceLinkId(UUID sourceLinkId);
}
