package roundtrip.credit.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.market.domain.entity.CreditHistory;

import java.util.List;
import java.util.UUID;

interface CreditHistoryQueryJpaRepository extends JpaRepository<CreditHistory, UUID> {

    @Query("""
        SELECT COALESCE(SUM(c.amount), 0) FROM CreditHistory c
        WHERE c.userId = :userId
        AND c.amount > 0
    """)
    long sumEarnedByUserId(@Param("userId") UUID userID);

    @Query("""
        SELECT COALESCE(-SUM(c.amount), 0) FROM CreditHistory c
        WHERE c.userId = :userId
        AND c.amount < 0
    """)
    long sumSpentByUserId(@Param("userId") UUID userID);

    @Query("""
      SELECT c FROM CreditHistory c
      WHERE c.userId = :userId
        AND (:type IS NULL
             OR (:type = 'earned' AND c.amount > 0)
             OR (:type = 'spent'  AND c.amount < 0))
        AND (:cursor IS NULL
             OR c.createdAt < (SELECT c2.createdAt FROM CreditHistory c2 WHERE c2.id = :cursor)
             OR (c.createdAt = (SELECT c2.createdAt FROM CreditHistory c2 WHERE c2.id = :cursor)
                 AND c.id < :cursor))
      ORDER BY c.createdAt DESC, c.id DESC
      LIMIT :limit
      """)
    List<CreditHistory> findByUserIdWithCursor(@Param("userId") UUID userId,
                                               @Param("type") String type,
                                               @Param("limit") int limit,
                                               @Param("cursor") UUID cursor);
}
