package roundtrip.place.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private PlaceCategory category;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "kakao_place_id", length = 255)
    private String kakaoPlaceId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "thumbnail_source", length = 20)
    private ThumbnailSource thumbnailSource;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    public void updateThumbnail(String thumbnailUrl, ThumbnailSource thumbnailSource) {
        this.thumbnailUrl = thumbnailUrl;
        this.thumbnailSource = thumbnailSource;
    }

    public static Place create(String canonicalName, BigDecimal latitude, BigDecimal longitude,
                               PlaceCategory category, String countryCode,
                               String kakaoPlaceId, String evidence) {
        var place = new Place();
        place.canonicalName = canonicalName;
        place.latitude = latitude;
        place.longitude = longitude;
        place.category = category;
        place.countryCode = countryCode;
        place.kakaoPlaceId = kakaoPlaceId;
        place.evidence = evidence;
        return place;
    }
}
