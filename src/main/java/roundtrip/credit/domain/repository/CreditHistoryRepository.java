package roundtrip.credit.domain.repository;

import roundtrip.market.domain.entity.CreditHistory;

import java.util.List;
import java.util.UUID;

public interface CreditHistoryRepository {
    CreditHistory save(CreditHistory creditHistory);
    long sumEarnedByUserId(UUID userId);
    long sumSpentByUserId(UUID userId);

    List<CreditHistory> findByUserIdWithCursor(UUID user, String type, int limit, UUID cursor);
}
