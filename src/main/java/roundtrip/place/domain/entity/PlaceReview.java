package roundtrip.place.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "place_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "place_id", nullable = false, columnDefinition = "uuid")
    private UUID placeId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static PlaceReview create(UUID placeId, UUID userId, short rating, String body) {
        Objects.requireNonNull(placeId, "placeId는 null일 수 없습니다");
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다");
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating은 1~5 사이여야 합니다");
        }

        var review = new PlaceReview();
        review.placeId = placeId;
        review.userId = userId;
        review.rating = rating;
        review.body = body;
        review.createdAt = OffsetDateTime.now();
        return review;
    }
}
