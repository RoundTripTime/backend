package roundtrip.notification.infrastructure.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Admin 자격증명 설정. {@code credentialsPath}가 지정된 경우에만 FCM 전송이 활성화된다.
 * Spring Resource 경로 형식을 따른다(예: {@code file:/etc/secrets/firebase.json}, {@code classpath:firebase.json}).
 */
@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(
        String credentialsPath
) {
}
