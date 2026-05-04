package roundtrip.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(name = "home_region", length = 100)
    private String homeRegion;

    @Column(name = "map_provider", nullable = false, length = 20)
    private String mapProvider;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static User create(String nickname, String email, String avatarUrl, String locale) {
        var user = new User();
        user.nickname = nickname;
        user.email = email;
        user.avatarUrl = avatarUrl;
        user.locale = (locale != null) ? locale : "ko-KR";
        user.mapProvider = "kakao";
        return user;
    }

    public void updateProfile(String nickname, String avatarUrl, String homeRegion,
                              String locale, String mapProvider) {
        if (nickname != null) this.nickname = nickname;
        if (avatarUrl != null) this.avatarUrl = avatarUrl;
        if (homeRegion != null) this.homeRegion = homeRegion;
        if (locale != null) this.locale = locale;
        if (mapProvider != null) this.mapProvider = mapProvider;
    }
}
