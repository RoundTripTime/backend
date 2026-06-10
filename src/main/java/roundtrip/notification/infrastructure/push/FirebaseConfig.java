package roundtrip.notification.infrastructure.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * {@code firebase.credentials-path}가 설정된 경우에만 {@link FirebaseMessaging} 빈을 생성한다.
 * 자격증명이 없으면 빈이 등록되지 않으므로, FCM 전송은 비활성화되고 DB 알림 생성만 동작한다.
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${firebase.credentials-path:}' != ''")
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FirebaseProperties properties;
    private final ResourceLoader resourceLoader;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        Resource resource = resourceLoader.getResource(properties.credentialsPath());
        try (InputStream credentials = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("FirebaseApp 초기화 완료 (credentialsPath={})", properties.credentialsPath());
            return app;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
