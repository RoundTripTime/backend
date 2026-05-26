package roundtrip.notification.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Notification create(UUID userId, String type, UUID jobId, String message) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다");
        Objects.requireNonNull(type, "type은 null일 수 없습니다");
        Objects.requireNonNull(message, "message는 null일 수 없습니다");

        var n = new Notification();
        n.userId = userId;
        n.type = type;
        n.jobId = jobId;
        n.message = message;
        n.isRead = false;
        n.createdAt = OffsetDateTime.now();
        return n;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
