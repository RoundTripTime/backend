package roundtrip.credit.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ad_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdSession extends BaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted = false;

    public static AdSession start(UUID userId, Duration ttl){
        var session = new AdSession();
        session.userId = userId;
        session.expiresAt = OffsetDateTime.now().plus(ttl);
        session.isCompleted = false;
        return session;
    }

    public void complete(){
        if(isCompleted){
            throw new BusinessException(ErrorCode.AD_ALREADY_COMPLETED);
        }
        if(OffsetDateTime.now().isAfter(expiresAt)){
            throw new BusinessException(ErrorCode.INVALID_AD_SESSION);
        }
        this.isCompleted = true;
    }
}
