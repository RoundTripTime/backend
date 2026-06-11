-- ──────────────── notifications.type 에 추출 검토 리마인드 타입 추가 ────────────────
-- 추출 완료 후 일정 시간 동안 확인하지 않은 후보가 있을 때 보내는 리마인드 알림 타입
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('job_completed', 'job_failed', 'extraction_review_reminder'));
