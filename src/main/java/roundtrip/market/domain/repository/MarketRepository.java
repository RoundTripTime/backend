package roundtrip.market.domain.repository;

import roundtrip.market.domain.entity.MarketPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketRepository {

    // ── Market Plans ──
    MarketPlan savePlan(MarketPlan plan);

    Optional<MarketPlan> findPlanById(UUID id);

    boolean existsByItineraryId(UUID itineraryId);

    List<MarketPlan> findListedPlans(String keyword, String sort, UUID cursorId, int limit);

    // ── Credits ──
    boolean hasPurchased(UUID userId, UUID marketPlanId);
}
