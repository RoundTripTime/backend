package roundtrip.sourcelink.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "source_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceLink extends BaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 30)
    private SourceType sourceType;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "normalized_url_hash", nullable = false, length = 64)
    private String normalizedUrlHash;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LinkStatus status;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    public static SourceLink create(UUID userId, String url, String normalizedUrlHash, SourceType sourceType) {
        var link = new SourceLink();
        link.userId = userId;
        link.url = url;
        link.normalizedUrlHash = normalizedUrlHash;
        link.sourceType = sourceType;
        link.status = LinkStatus.PENDING;
        link.visibility = "private";
        link.submittedAt = OffsetDateTime.now();
        return link;
    }

    public void markProcessing() {
        this.status = LinkStatus.PROCESSING;
    }

    public void markDone(String title, String thumbnailUrl) {
        this.status = LinkStatus.DONE;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void markFailed() {
        this.status = LinkStatus.FAILED;
    }
}
