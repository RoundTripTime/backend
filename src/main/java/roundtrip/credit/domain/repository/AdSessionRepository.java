package roundtrip.credit.domain.repository;

import roundtrip.credit.domain.entity.AdSession;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AdSessionRepository {
    AdSession save(AdSession adSession);
    Optional<AdSession> findById(UUID id);
    long countCompletedBetween(UUID userId, OffsetDateTime startOfDay, OffsetDateTime startOfNextDay);
}
