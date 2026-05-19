package roundtrip.sourcelink.application;

import java.util.UUID;

/**
 * source_link 저장 + extraction_job 생성 트랜잭션 커밋 후 파이프라인을 시작하기 위한 이벤트.
 * @TransactionalEventListener(phase = AFTER_COMMIT)으로 소비한다.
 */
public record ExtractionTriggeredEvent(UUID jobId, UUID sourceLinkId) {}
