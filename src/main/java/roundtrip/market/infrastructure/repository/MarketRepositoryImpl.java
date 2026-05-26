package roundtrip.market.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.market.domain.entity.CreditHistory;
import roundtrip.market.domain.entity.MarketPlan;
import roundtrip.market.domain.repository.MarketRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MarketRepositoryImpl implements MarketRepository {

    private final MarketPlanJpaRepository planJpa;
    private final CreditHistoryJpaRepository creditJpa;
    private final EntityManager em;

    @Override
    public MarketPlan savePlan(MarketPlan plan) {
        return planJpa.save(plan);
    }

    @Override
    public Optional<MarketPlan> findPlanById(UUID id) {
        return planJpa.findById(id);
    }

    @Override
    public boolean existsByItineraryId(UUID itineraryId) {
        return planJpa.existsByItineraryId(itineraryId);
    }

    @Override
    public List<MarketPlan> findListedPlans(String keyword, String sort, UUID cursorId, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT mp FROM MarketPlan mp WHERE mp.isListed = true");

        if (keyword != null && !keyword.isBlank()) {
            jpql.append(" AND (LOWER(mp.title) LIKE :kw OR LOWER(mp.highlight) LIKE :kw)");
        }
        if (cursorId != null) {
            if ("popular".equals(sort)) {
                jpql.append(" AND (mp.viewCount < (SELECT mp2.viewCount FROM MarketPlan mp2 WHERE mp2.id = :cursorId)" +
                    " OR (mp.viewCount = (SELECT mp2.viewCount FROM MarketPlan mp2 WHERE mp2.id = :cursorId)" +
                    " AND mp.createdAt < (SELECT mp2.createdAt FROM MarketPlan mp2 WHERE mp2.id = :cursorId)))");
            } else {
                jpql.append(" AND mp.createdAt < (SELECT mp2.createdAt FROM MarketPlan mp2 WHERE mp2.id = :cursorId)");
            }
        }

        if ("popular".equals(sort)) {
            jpql.append(" ORDER BY mp.viewCount DESC, mp.createdAt DESC");
        } else {
            jpql.append(" ORDER BY mp.createdAt DESC");
        }

        TypedQuery<MarketPlan> query = em.createQuery(jpql.toString(), MarketPlan.class);
        if (keyword != null && !keyword.isBlank()) {
            query.setParameter("kw", "%" + keyword.toLowerCase() + "%");
        }
        if (cursorId != null) {
            query.setParameter("cursorId", cursorId);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public CreditHistory saveCredit(CreditHistory history) {
        return creditJpa.save(history);
    }

    @Override
    public boolean hasPurchased(UUID userId, UUID marketPlanId) {
        return creditJpa.hasPurchased(userId, marketPlanId);
    }
}
