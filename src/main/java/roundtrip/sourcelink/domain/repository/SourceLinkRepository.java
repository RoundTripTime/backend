package roundtrip.sourcelink.domain.repository;

import roundtrip.sourcelink.domain.entity.LinkStatus;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceLinkRepository {

    SourceLink save(SourceLink sourceLink);

    Optional<SourceLink> findById(UUID id);

    boolean existsByUserIdAndNormalizedUrlHash(UUID userId, String normalizedUrlHash);

    List<SourceLink> findByUserIdWithCursor(UUID userId, LinkStatus status, int limit, UUID cursor);
}
