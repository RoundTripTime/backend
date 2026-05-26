package roundtrip.credit.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.credit.domain.entity.AdSession;
import roundtrip.credit.domain.repository.AdSessionRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AdSessionRepositoryImpl implements AdSessionRepository {

    private final AdSessionJpaRepository jpa;


    @Override
    public AdSession save(AdSession adSession) {
        return jpa.save(adSession);
    }

    @Override
    public Optional<AdSession> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public long countCompletedBetween(UUID userId, OffsetDateTime startOfDay, OffsetDateTime startOfNextDay) {
        return jpa.countCompletedBetween(userId, startOfDay, startOfNextDay);
    }
}
