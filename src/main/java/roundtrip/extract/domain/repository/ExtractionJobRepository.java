package roundtrip.extract.domain.repository;

import roundtrip.extract.domain.entity.ExtractionJob;

import java.util.Optional;
import java.util.UUID;

public interface ExtractionJobRepository {

    ExtractionJob save(ExtractionJob job);

    Optional<ExtractionJob> findById(UUID id);

    Optional<ExtractionJob> findBySourceLinkId(UUID sourceLinkId);
}
