package roundtrip.candidate.presentation.dto;

import roundtrip.place.domain.entity.Place;
import roundtrip.candidate.application.PlaceCandidateService;
import roundtrip.candidate.domain.entity.PlaceCandidate;

import java.math.BigDecimal;
import java.util.UUID;

public record CandidateResponse(
        UUID id,
        UUID jobId,
        String candidateName,
        String category,
        BigDecimal confidenceScore,
        int rankOrder,
        boolean requiresConfirmation,
        String status,
        String evidence,
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
