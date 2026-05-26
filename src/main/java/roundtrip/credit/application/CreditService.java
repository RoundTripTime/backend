package roundtrip.credit.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.market.domain.entity.CreditHistory;
import roundtrip.credit.domain.repository.CreditHistoryRepository;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditService {
    private final UserRepository userRepository;
    private final CreditHistoryRepository creditHistoryRepository;

    @Transactional(readOnly = true)
    public MyCreditResult getMyCredit(UUID userId){
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long earned = creditHistoryRepository.sumEarnedByUserId(userId);
        long spent = creditHistoryRepository.sumSpentByUserId(userId);
        return new MyCreditResult(user.getCreditBalance(), earned, spent);
    }

    @Transactional(readOnly = true)
    public List<CreditHistory> getMyHistory(UUID userId, String type, int limit, UUID cursor){
        return creditHistoryRepository.findByUserIdWithCursor(userId, type, limit, cursor);
    }

    @Transactional
    public EarnResult earn(UUID userId, String creditType, int amount, String description){
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.addCredit(amount);
        int newBalance = user.getCreditBalance();

        CreditHistory ch = CreditHistory.create(userId, creditType, amount, newBalance, description);
        creditHistoryRepository.save(ch);

        return new EarnResult(newBalance);
    }

    @Transactional
    public SpendResult spend(UUID userId, String creditType, int amount, String description) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.subtractCredit(amount);  // 잔액 부족 시 INSUFFICIENT_CREDITS
        CreditHistory ch = CreditHistory.create(userId, creditType, -amount, user.getCreditBalance(), description);
        creditHistoryRepository.save(ch);
        return new SpendResult(user.getCreditBalance());
    }

    public record SpendResult(int balance) {}
    public record MyCreditResult(int balance, long lifetimeEarned, long lifetimeSpent) {}
    public record EarnResult(int balance) {}
}
