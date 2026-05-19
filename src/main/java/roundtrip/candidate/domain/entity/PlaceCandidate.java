package roundtrip.candidate.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import roundtrip.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "place_candidates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCandidate extends BaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false, columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "place_id", columnDefinition = "uuid")
    private UUID placeId;

    @Column(name = "candidate_name", nullable = false, length = 255)
    private String candidateName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_match_json", columnDefinition = "JSONB")
    private String providerMatchJson;

    @Column(name = "requires_confirmation", nullable = false)
    private boolean requiresConfirmation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CandidateStatus status;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    public static PlaceCandidate create(UUID jobId, UUID placeId, String candidateName,
                                        String category, BigDecimal confidenceScore, int rankOrder,
                                        boolean requiresConfirmation, String evidence,
                                        String providerMatchJson) {
        var candidate = new PlaceCandidate();
        candidate.jobId = jobId;
        candidate.placeId = placeId;
        candidate.candidateName = candidateName;
        candidate.category = category;
        candidate.confidenceScore = confidenceScore;
        candidate.rankOrder = rankOrder;
        candidate.requiresConfirmation = requiresConfirmation;
        candidate.evidence = evidence;
        candidate.providerMatchJson = providerMatchJson;
        candidate.status = CandidateStatus.PROPOSED;
        return candidate;
    }

    public void accept() {
        this.status = CandidateStatus.ACCEPTED;
    }

    public void reject() {
        this.status = CandidateStatus.REJECTED;
    }

    public void edit(String name) {
        this.candidateName = name;
        this.status = CandidateStatus.EDITED;
    }
}
