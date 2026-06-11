package roundtrip.notification.domain.repository;

import roundtrip.notification.application.ReviewReminderTarget;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReviewReminderRepository {

    /**
     * 검토 리마인드 알림을 보내야 하는 작업을 조회한다. 조건:
     * <ul>
     *   <li>작업이 완료(DONE)되었고 {@code completedBefore} 이전에 완료됨</li>
     *   <li>아직 검토하지 않은(PROPOSED) 후보가 하나라도 존재</li>
     *   <li>해당 작업에 대한 리마인드 알림이 아직 발송되지 않음</li>
     * </ul>
     */
    List<ReviewReminderTarget> findJobsNeedingReviewReminder(OffsetDateTime completedBefore);
}
