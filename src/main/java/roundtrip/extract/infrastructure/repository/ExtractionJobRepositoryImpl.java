package roundtrip.extract.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.extract.domain.repository.ExtractionJobRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ExtractionJobRepositoryImpl implements ExtractionJobRepository {

    private final ExtractionJobJpaRepository jpa;

    @Override
    public ExtractionJob save(ExtractionJob job) {
        return jpa.save(job);
    }

    @Override
    public Optional<ExtractionJob> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<ExtractionJob> findBySourceLinkId(UUID sourceLinkId) {
        return jpa.findBySourceLinkId(sourceLinkId);
    }
}
