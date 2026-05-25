package roundtrip.candidate.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.domain.entity.Place;
import roundtrip.candidate.application.PlaceCandidateService;
import roundtrip.candidate.domain.entity.PlaceCandidate;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "장소 후보")
public record CandidateResponse(
    @Schema(description = "후보 고유 ID")
    UUID id,
    @Schema(description = "연결된 분석 잡 ID")
    UUID jobId,
    @Schema(description = "AI가 파싱한 장소명", example = "시부야 스크램블 교차로")
    String candidateName,
    @Schema(description = "장소 카테고리", example = "관광명소")
    String category,
    @Schema(description = "AI 추출 신뢰도 (0.0~1.0)", example = "0.92")
    BigDecimal confidenceScore,
    @Schema(description = "영상 내 언급 순서 (1부터 시작)", example = "1")
    int rankOrder,
    @Schema(description = "신뢰도 낮은 후보 여부. true이면 사용자 수동 확인 권장", example = "false")
    boolean requiresConfirmation,
    @Schema(description = "후보 상태", example = "proposed",
        allowableValues = {"proposed", "accepted", "rejected", "edited"})
    String status,
    @Schema(description = "장소 추출 근거 텍스트", example = "캡션: 시부야 교차로 최고의 뷰포인트")
    String evidence,
    @Schema(description = "지도 정규화된 장소 정보. 매칭 실패 시 null", nullable = true)
    PlaceInfo place
) {
    public static CandidateResponse from(PlaceCandidate candidate, Place place) {
        return new CandidateResponse(
                candidate.getId(),
                candidate.getJobId(),
                candidate.getCandidateName(),
                candidate.getCategory(),
                candidate.getConfidenceScore(),
                candidate.getRankOrder(),
                candidate.isRequiresConfirmation(),
                candidate.getStatus().name().toLowerCase(),
                candidate.getEvidence(),
                PlaceInfo.from(place)
        );
    }

    public static CandidateResponse from(PlaceCandidateService.CandidateWithPlace cwp) {
        return from(cwp.candidate(), cwp.place());
    }
}
