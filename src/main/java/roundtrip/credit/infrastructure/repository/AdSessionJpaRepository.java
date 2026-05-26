package roundtrip.credit.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.credit.domain.entity.AdSession;

import java.time.OffsetDateTime;
import java.util.UUID;

interface AdSessionJpaRepository extends JpaRepository<AdSession, UUID> {

    @Query("""
        SELECT COUNT(s) FROM AdSession s
        WHERE s.userId = :userId
            AND s.isCompleted = true
            AND s.createdAt >= :startOfDay
            AND s.createdAt < :startOfNextDay
    """)
    long countCompletedBetween(@Param("userId") UUID userId,
                               @Param("startOfDay")OffsetDateTime startOfDay,
                               @Param("startOfNextDay") OffsetDateTime startOfNextDay);
}
