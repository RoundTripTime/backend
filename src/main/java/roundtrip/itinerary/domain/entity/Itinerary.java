package roundtrip.itinerary.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "itineraries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "destination_region", nullable = false, length = 100)
    private String destinationRegion;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility;

    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Itinerary create(UUID userId, String title, String destinationRegion,
                                   LocalDate startDate, LocalDate endDate, int partySize) {
        Itinerary it = new Itinerary();
        it.userId = userId;
        it.title = title;
        it.destinationRegion = destinationRegion;
        it.startDate = startDate;
        it.endDate = endDate;
        it.partySize = partySize;
        it.status = "draft";
        it.visibility = "private";
        it.createdAt = OffsetDateTime.now();
        return it;
    }

    public void update(String title, String destinationRegion, LocalDate startDate,
                       LocalDate endDate, Integer partySize, String visibility, String status) {
        if (title != null) this.title = title;
        if (destinationRegion != null) this.destinationRegion = destinationRegion;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (partySize != null) this.partySize = partySize;
        if (visibility != null) this.visibility = visibility;
        if (status != null) this.status = status;
    }

    public String ensureShareToken() {
        if (this.shareToken == null) {
            this.shareToken = UUID.randomUUID().toString().replace("-", "");
        }
        return this.shareToken;
    }
}
