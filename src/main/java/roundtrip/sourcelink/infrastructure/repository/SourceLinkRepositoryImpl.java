package roundtrip.sourcelink.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.sourcelink.domain.entity.LinkStatus;
import roundtrip.sourcelink.domain.entity.SourceLink;
import roundtrip.sourcelink.domain.repository.SourceLinkRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SourceLinkRepositoryImpl implements SourceLinkRepository {

    private final SourceLinkJpaRepository jpa;

    @Override
    public SourceLink save(SourceLink sourceLink) {
        return jpa.save(sourceLink);
    }

    @Override
    public Optional<SourceLink> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existsByUserIdAndNormalizedUrlHash(UUID userId, String normalizedUrlHash) {
        return jpa.existsByUserIdAndNormalizedUrlHash(userId, normalizedUrlHash);
    }

    @Override
    public List<SourceLink> findByUserIdWithCursor(UUID userId, LinkStatus status, int limit, UUID cursor) {
        return jpa.findByUserIdWithCursor(userId, status, limit, cursor);
    }
}
