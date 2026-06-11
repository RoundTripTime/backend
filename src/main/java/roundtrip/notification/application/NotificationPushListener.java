package roundtrip.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import roundtrip.notification.infrastructure.push.FcmPushService;

import java.util.HashMap;
import java.util.Map;

/**
 * 알림 생성 이벤트를 받아 FCM 푸시를 전송한다.
 * 트랜잭션 커밋 후(비동기)에 동작하며, 트랜잭션이 없는 경우에도 실행되도록 fallback을 허용한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private static final String PUSH_TITLE = "라운드트립";

    private final FcmPushService fcmPushService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        Map<String, String> data = new HashMap<>();
        data.put("type", event.type().value());
        if (event.jobId() != null) {
            data.put("jobId", event.jobId().toString());
        }
        fcmPushService.sendToUser(event.userId(), PUSH_TITLE, event.message(), data);
    }
}
