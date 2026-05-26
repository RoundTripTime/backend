package roundtrip.market.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "market_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "itinerary_id", nullable = false, columnDefinition = "uuid")
    private UUID itineraryId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "highlight", nullable = false, length = 100)
    private String highlight;

    @Column(name = "pros", nullable = false, columnDefinition = "TEXT")
    private String pros;

    @Column(name = "cons", nullable = false, columnDefinition = "TEXT")
    private String cons;

    @Column(name = "tips", columnDefinition = "TEXT")
    private String tips;

    @Column(name = "credit_price", nullable = false)
    private int creditPrice;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "is_listed", nullable = false)
    private boolean isListed;

    @Column(name = "ota_booked_at")
    private LocalDate otaBookedAt;

    @Column(name = "ota_verified", nullable = false)
    private boolean otaVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static MarketPlan create(UUID itineraryId, UUID userId, String title, String description,
                                     String highlight, String pros, String cons, String tips) {
        Objects.requireNonNull(itineraryId);
        Objects.requireNonNull(userId);

        var mp = new MarketPlan();
        mp.itineraryId = itineraryId;
        mp.userId = userId;
        mp.title = title;
        mp.description = description;
        mp.highlight = highlight;
        mp.pros = pros;
        mp.cons = cons;
        mp.tips = tips;
        mp.creditPrice = 1;
        mp.viewCount = 0;
        mp.isListed = true;
        mp.otaVerified = false;
        mp.createdAt = OffsetDateTime.now();
        return mp;
    }

    public void unlist() {
        this.isListed = false;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }
}
