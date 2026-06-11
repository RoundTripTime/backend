package roundtrip.notification.domain.entity;

/**
 * 알림 종류. {@link #value}는 notifications.type 컬럼에 저장되는 문자열이며 DB CHECK 제약과 일치해야 한다.
 */
public enum NotificationType {

    JOB_COMPLETED("job_completed"),
    JOB_FAILED("job_failed"),
    EXTRACTION_REVIEW_REMINDER("extraction_review_reminder");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
