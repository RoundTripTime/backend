package roundtrip.notification.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;
import roundtrip.common.entity.BaseEntity;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseEntity<UUID> {

    private static final Set<String> ALLOWED_PLATFORMS = Set.of("ios", "android", "web");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "token", nullable = false, length = 512, unique = true)
    private String token;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static DeviceToken create(UUID userId, String token, String platform) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다");
        Objects.requireNonNull(token, "token은 null일 수 없습니다");

        var deviceToken = new DeviceToken();
        deviceToken.userId = userId;
        deviceToken.token = token;
        deviceToken.platform = normalizePlatform(platform);
        return deviceToken;
    }

    /**
     * 이미 등록된 토큰이 다른 사용자/플랫폼으로 재등록될 때 소유자와 플랫폼을 갱신한다.
     */
    public void reassign(UUID userId, String platform) {
        this.userId = userId;
        this.platform = normalizePlatform(platform);
    }

    private static String normalizePlatform(String platform) {
        Objects.requireNonNull(platform, "platform은 null일 수 없습니다");
        String normalized = platform.toLowerCase();
        if (!ALLOWED_PLATFORMS.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 platform입니다: " + platform);
        }
        return normalized;
    }
}
