package roundtrip.notification.infrastructure.push;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import roundtrip.notification.domain.entity.DeviceToken;
import roundtrip.notification.domain.repository.DeviceTokenRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 사용자의 등록된 디바이스 토큰으로 FCM 푸시를 전송한다.
 * Firebase 자격증명이 설정되지 않아 {@link FirebaseMessaging} 빈이 없으면 전송을 건너뛴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final DeviceTokenRepository deviceTokenRepository;

    @Async
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.debug("FCM 미설정으로 푸시 전송을 건너뜁니다. userId={}", userId);
            return;
        }

        List<String> tokens = deviceTokenRepository.findByUserId(userId).stream()
                .map(DeviceToken::getToken)
                .toList();
        if (tokens.isEmpty()) {
            log.debug("등록된 디바이스 토큰이 없어 푸시를 건너뜁니다. userId={}", userId);
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();

        try {
            BatchResponse response = messaging.sendEachForMulticast(message);
            cleanUpInvalidTokens(tokens, response);
            log.info("FCM 푸시 전송 완료. userId={}, success={}, failure={}",
                    userId, response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 푸시 전송 실패. userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * UNREGISTERED / INVALID_ARGUMENT 로 실패한 토큰은 더 이상 유효하지 않으므로 정리한다.
     */
    private void cleanUpInvalidTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        List<String> invalidTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = sendResponse.getException() != null
                    ? sendResponse.getException().getMessagingErrorCode()
                    : null;
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidTokens.add(tokens.get(i));
            }
        }
        if (!invalidTokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(invalidTokens);
            log.info("무효 디바이스 토큰 {}개 정리", invalidTokens.size());
        }
    }
}
