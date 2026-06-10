package roundtrip.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import roundtrip.notification.domain.repository.ReviewReminderRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 추출 완료 후 일정 시간이 지나도록 사용자가 확인하지 않은(PROPOSED 후보가 남은) 작업에 대해
 * "추출한 장소를 확인해주세요!" 리마인드 알림을 주기적으로 발송한다. 작업당 1회만 발송된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionReviewReminderScheduler {

    private final ReviewReminderRepository reviewReminderRepository;
    private final NotificationService notificationService;

    @Value("${notification.review-reminder.delay-minutes:30}")
    private long delayMinutes;

    @Scheduled(fixedDelayString = "${notification.review-reminder.scan-interval-ms:300000}")
    public void sendReviewReminders() {
        OffsetDateTime completedBefore = OffsetDateTime.now().minusMinutes(delayMinutes);
        List<ReviewReminderTarget> targets = reviewReminderRepository.findJobsNeedingReviewReminder(completedBefore);
        if (targets.isEmpty()) {
            return;
        }

        int sent = 0;
        for (ReviewReminderTarget target : targets) {
            try {
                notificationService.createReviewReminder(target.userId(), target.jobId());
                sent++;
            } catch (Exception e) {
                log.warn("검토 리마인드 알림 발송 실패. jobId={}, userId={}, error={}",
                        target.jobId(), target.userId(), e.getMessage());
            }
        }
        log.info("추출 검토 리마인드 알림 {}건 발송", sent);
    }
}
