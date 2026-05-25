package roundtrip.candidate.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import roundtrip.candidate.domain.entity.CandidateStatus;

@Schema(description = "후보 상태 변경 요청")
public record UpdateCandidateRequest(
    @Schema(description = "변경할 상태", example = "accepted",
        allowableValues = {"accepted", "rejected", "edited"})
    @NotNull CandidateStatus status
) {
}
