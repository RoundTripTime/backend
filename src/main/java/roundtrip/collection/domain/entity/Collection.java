package roundtrip.collection.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Collection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "icon", length = 10)
    private String icon;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "visibility", nullable = false, length = 10)
    private String visibility;

    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Collection createDefault(UUID userId) {
        Collection c = new Collection();
        c.userId = userId;
        c.name = "저장한 플레이스";
        c.isDefault = true;
        c.visibility = "private";
        c.createdAt = OffsetDateTime.now();
        return c;
    }

    public static Collection create(UUID userId, String name, String icon) {
        Collection c = new Collection();
        c.userId = userId;
        c.name = name;
        c.icon = icon;
        c.isDefault = false;
        c.visibility = "private";
        c.createdAt = OffsetDateTime.now();
        return c;
    }

    public void update(String name, String icon, String visibility) {
        if (name != null) this.name = name;
        if (icon != null) this.icon = icon;
        if (visibility != null) this.visibility = visibility;
    }

    public String ensureShareToken() {
        if (this.shareToken == null) {
            this.shareToken = UUID.randomUUID().toString().replace("-", "");
        }
        return this.shareToken;
    }
}
