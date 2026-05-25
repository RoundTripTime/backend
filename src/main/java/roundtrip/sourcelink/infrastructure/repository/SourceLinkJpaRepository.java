package roundtrip.sourcelink.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.sourcelink.domain.entity.LinkStatus;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.util.List;
import java.util.UUID;

interface SourceLinkJpaRepository extends JpaRepository<SourceLink, UUID> {

    boolean existsByUserIdAndNormalizedUrlHash(UUID userId, String normalizedUrlHash);

    @Query("""
            SELECT s FROM SourceLink s
            WHERE s.userId = :userId
              AND (:status IS NULL OR s.status = :status)
              AND (:cursor IS NULL OR s.id > :cursor)
            ORDER BY s.id ASC
            LIMIT :limit
            """)
    List<SourceLink> findByUserIdWithCursor(
            @Param("userId") UUID userId,
            @Param("status") LinkStatus status,
            @Param("limit") int limit,
            @Param("cursor") UUID cursor
    );
}
