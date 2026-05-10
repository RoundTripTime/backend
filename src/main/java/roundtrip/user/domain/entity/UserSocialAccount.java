package roundtrip.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocialAccount extends BaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "social_id", nullable = false)
    private String socialId;

    public static UserSocialAccount link(UUID userId, SocialProvider provider, String socialId) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다");
        Objects.requireNonNull(provider, "provider는 null일 수 없습니다");
        requireNonBlank(socialId, "socialId");

        var account = new UserSocialAccount();
        account.userId = userId;
        account.provider = provider;
        account.socialId = socialId;
        return account;
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "은(는) null일 수 없습니다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어있을 수 없습니다");
        }
    }
}
