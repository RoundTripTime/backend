package roundtrip.market.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.market.domain.entity.CreditHistory;

import java.util.UUID;

interface CreditHistoryJpaRepository extends JpaRepository<CreditHistory, UUID> {

    @Query("SELECT CASE WHEN COUNT(ch) > 0 THEN true ELSE false END FROM CreditHistory ch " +
           "WHERE ch.userId = :userId AND ch.creditType = 'plan_purchase' " +
           "AND ch.description LIKE CONCAT('%', CAST(:marketPlanId AS string), '%')")
    boolean hasPurchased(@Param("userId") UUID userId, @Param("marketPlanId") UUID marketPlanId);
}
