package roundtrip.market.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.market.domain.entity.MarketPlan;

import java.util.UUID;

interface MarketPlanJpaRepository extends JpaRepository<MarketPlan, UUID> {

    boolean existsByItineraryId(UUID itineraryId);
}
