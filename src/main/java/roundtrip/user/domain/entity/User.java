package roundtrip.user.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;
import roundtrip.user.domain.vo.Email;
import roundtrip.user.domain.vo.EmailConverter;
import roundtrip.user.domain.vo.Nickname;
import roundtrip.user.domain.vo.NicknameConverter;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Convert(converter = EmailConverter.class)
    private Email email;

    @Convert(converter = NicknameConverter.class)
    @Column(nullable = false, length = 50)
    private Nickname nickname;

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

    public static User register(Email email, Nickname nickname, String avatarUrl,
                                String locale, String homeRegion) {
        Objects.requireNonNull(nickname, "nickname은 null일 수 없습니다");
        requireNonBlank(locale, "locale");
        requireNonBlank(homeRegion, "homeRegion");

        var user = new User();
        user.email = email;
        user.nickname = nickname;
        user.avatarUrl = avatarUrl;
        user.locale = locale;
        user.homeRegion = homeRegion;
        user.mapProvider = MapProvider.KAKAO;
        user.creditBalance = 0;
        return user;
    }

    public void changeNickname(Nickname newNickname) {
        Objects.requireNonNull(newNickname, "nickname은 null일 수 없습니다");
        this.nickname = newNickname;
    }

    public void changeAvatar(String newAvatarUrl) {
        this.avatarUrl = newAvatarUrl;
    }

    public void changeHomeRegion(String newHomeRegion) {
        requireNonBlank(newHomeRegion, "homeRegion");
        this.homeRegion = newHomeRegion;
    }

    public void changeLocale(String newLocale) {
        requireNonBlank(newLocale, "locale");
        this.locale = newLocale;
    }

    public void changeMapProvider(MapProvider newMapProvider) {
        Objects.requireNonNull(newMapProvider, "mapProvider는 null일 수 없습니다");
        this.mapProvider = newMapProvider;
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "은(는) null일 수 없습니다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어있을 수 없습니다");
        }
    }

    public void updateProfile(Nickname nickname, String avatarUrl, String homeRegion, String locale, MapProvider mapProvider) {
        if(nickname != null) changeNickname(nickname);
        if(avatarUrl != null) changeAvatar(avatarUrl);
        if(homeRegion != null) changeHomeRegion(homeRegion);
        if(locale != null) changeLocale(locale);
        if(mapProvider != null) changeMapProvider(mapProvider);
    }

    public void addCredit(int amount){
        if(amount <= 0){
            throw new IllegalArgumentException("적립 금액은 양수여야 합니다.");
        }
        this.creditBalance += amount;
    }

    public void subtractCredit(int amount){
        if(amount <= 0){
            throw new IllegalArgumentException("차감 금액은 양수여야 합니다.");
        }
        this.creditBalance -= amount;
    }
}
