package roundtrip.candidate.presentation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import roundtrip.candidate.domain.entity.CandidateStatus;

import java.util.List;
import java.util.UUID;

public record BatchCandidateRequest(
        @NotEmpty List<CandidateUpdateItem> candidates
) {
    public record CandidateUpdateItem(
            @NotNull UUID candidateId,
            @NotNull CandidateStatus status
    ) {}
}
