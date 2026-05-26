package roundtrip.credit.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.credit.domain.entity.AdSession;
import roundtrip.credit.domain.repository.AdSessionRepository;
import roundtrip.user.domain.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdSessionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);
    private static final int CREDITS_PER_BATCH = 5;
    private static final int DAILY_AD_LIMIT = 20; // TODO: 팀 정책 확정 필요

    private final AdSessionRepository adSessionRepository;
    private final CreditService creditService;
    private final UserRepository userRepository;

    @Transactional
    public StartResult start(UUID userId){
        long viewedToday = viewedToday(userId);
        if(viewedToday >= DAILY_AD_LIMIT){
            throw new BusinessException(ErrorCode.AD_LIMIT_REACHED);
        }

        AdSession session = adSessionRepository.save(AdSession.start(userId, SESSION_TTL));
        return new StartResult(session.getId(), session.getExpiresAt(), (int) viewedToday, CREDITS_PER_BATCH);
    }

    @Transactional
    public CompleteResult complete(UUID sessionId, UUID userId){
        AdSession session = adSessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AD_SESSION_NOT_FOUND));

        if(!session.getUserId().equals(userId)){
            throw new BusinessException(ErrorCode.AD_SESSION_NOT_FOUND);
        }

        session.complete();

        long viewedToday = viewedToday(userId);
        boolean creditEarned = viewedToday > 0 && viewedToday % CREDITS_PER_BATCH == 0;
        int balance;

        if(creditEarned){
            balance = creditService.earn(userId, "ad_view", 1, "광고 시청 보상").balance();
        }else {
            balance = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getCreditBalance();
        }
        return new CompleteResult((int) viewedToday, CREDITS_PER_BATCH, creditEarned, balance);
    }

    private long viewedToday(UUID userId){
        var todayKst = LocalDate.now(KST);
        var startOfDay = todayKst.atStartOfDay(KST).toOffsetDateTime();
        var startOfNextDay = todayKst.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        return adSessionRepository.countCompletedBetween(userId, startOfDay, startOfNextDay);
    }

    public record StartResult(UUID adSessionId, OffsetDateTime expiresAt, int viewedToday, int requiredForCredit) {}
    public record CompleteResult(int viewedToday, int requiredForCredit, boolean creditEarned, int balance) {}
}
