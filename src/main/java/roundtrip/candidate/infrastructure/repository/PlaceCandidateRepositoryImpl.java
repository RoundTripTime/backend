package roundtrip.candidate.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.domain.repository.PlaceCandidateRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceCandidateRepositoryImpl implements PlaceCandidateRepository {

    private final PlaceCandidateJpaRepository jpa;

    @Override
    public PlaceCandidate save(PlaceCandidate candidate) {
        return jpa.save(candidate);
    }

    @Override
    public List<PlaceCandidate> saveAll(List<PlaceCandidate> candidates) {
        return jpa.saveAll(candidates);
    }

    @Override
    public Optional<PlaceCandidate> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<PlaceCandidate> findByJobId(UUID jobId) {
        return jpa.findByJobId(jobId);
    }

    @Override
    public Optional<PlaceCandidate> findFirstByPlaceId(UUID placeId) {
        return jpa.findFirstByPlaceId(placeId);
    }

    @Override
    public List<PlaceCandidate> findByPlaceId(UUID placeId) {
        return jpa.findByPlaceId(placeId);
    }
}
