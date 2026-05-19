package roundtrip.candidate.domain.repository;

import roundtrip.candidate.domain.entity.PlaceCandidate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceCandidateRepository {

    PlaceCandidate save(PlaceCandidate candidate);

    List<PlaceCandidate> saveAll(List<PlaceCandidate> candidates);

    Optional<PlaceCandidate> findById(UUID id);

    List<PlaceCandidate> findByJobId(UUID jobId);

    Optional<PlaceCandidate> findFirstByPlaceId(UUID placeId);

    List<PlaceCandidate> findByPlaceId(UUID placeId);
}
