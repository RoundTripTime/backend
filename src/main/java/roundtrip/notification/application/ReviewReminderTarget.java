package roundtrip.notification.application;

import java.util.UUID;

/**
 * 추출 검토 리마인드 알림을 보내야 하는 대상(작업 ID + 사용자 ID).
 */
public record ReviewReminderTarget(
        UUID jobId,
        UUID userId
) {
}
