package roundtrip.parsing.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "extraction_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractionJob extends BaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_link_id", nullable = false, columnDefinition = "uuid")
    private UUID sourceLinkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 20)
    private JobStatus jobStatus;

    @Column(name = "signal_count", nullable = false)
    private int signalCount;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public static ExtractionJob create(UUID sourceLinkId) {
        var job = new ExtractionJob();
        job.sourceLinkId = sourceLinkId;
        job.jobStatus = JobStatus.PENDING;
        job.signalCount = 0;
        return job;
    }

    public void start() {
        this.jobStatus = JobStatus.PROCESSING;
        this.startedAt = OffsetDateTime.now();
    }

    public void complete(int signalCount) {
        this.jobStatus = JobStatus.DONE;
        this.signalCount = signalCount;
        this.completedAt = OffsetDateTime.now();
    }

    public void fail(String errorCode) {
        this.jobStatus = JobStatus.FAILED;
        this.errorCode = errorCode;
        this.completedAt = OffsetDateTime.now();
    }
}
