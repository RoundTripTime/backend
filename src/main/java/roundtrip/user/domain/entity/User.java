package roundtrip.user.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(name = "home_region", nullable = false, length = 100)
    private String homeRegion;

    @Column(name = "map_provider", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MapProvider mapProvider;

    @Column(name = "credit_balance", nullable = false)
    private int creditBalance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private User(String email, String nickname, String avatarUrl,
                 String locale, String homeRegion) {
        this.email = email;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.locale = locale;
        this.homeRegion = homeRegion;
        this.mapProvider = MapProvider.KAKAO;
        this.creditBalance = 0;
    }
}
