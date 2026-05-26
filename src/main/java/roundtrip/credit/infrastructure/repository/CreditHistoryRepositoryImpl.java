package roundtrip.credit.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.market.domain.entity.CreditHistory;
import roundtrip.credit.domain.repository.CreditHistoryRepository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CreditHistoryRepositoryImpl implements CreditHistoryRepository {

    private final CreditHistoryQueryJpaRepository jpa;

    @Override
    public CreditHistory save(CreditHistory creditHistory) {
        return jpa.save(creditHistory);
    }

    @Override
    public long sumEarnedByUserId(UUID userId) {
        return jpa.sumEarnedByUserId(userId);
    }

    @Override
    public long sumSpentByUserId(UUID userId) {
        return jpa.sumSpentByUserId(userId);
    }

    @Override
    public List<CreditHistory> findByUserIdWithCursor(UUID userId, String type, int limit, UUID cursor) {
        return jpa.findByUserIdWithCursor(userId, type, limit, cursor);
    }
}
