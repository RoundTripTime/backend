package roundtrip.candidate.presentation.dto;

import jakarta.validation.constraints.NotNull;
import roundtrip.candidate.domain.entity.CandidateStatus;

public record UpdateCandidateRequest(
        @NotNull CandidateStatus status
) {
}
