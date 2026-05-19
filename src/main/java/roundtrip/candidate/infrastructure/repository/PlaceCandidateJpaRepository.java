package roundtrip.candidate.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.candidate.domain.entity.PlaceCandidate;

import java.util.List;
import java.util.UUID;

interface PlaceCandidateJpaRepository extends JpaRepository<PlaceCandidate, UUID> {

    List<PlaceCandidate> findByJobId(UUID jobId);

    java.util.Optional<PlaceCandidate> findFirstByPlaceId(UUID placeId);

    List<PlaceCandidate> findByPlaceId(UUID placeId);
}
