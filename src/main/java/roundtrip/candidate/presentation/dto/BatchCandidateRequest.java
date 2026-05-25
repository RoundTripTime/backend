package roundtrip.candidate.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import roundtrip.candidate.domain.entity.CandidateStatus;

import java.util.List;
import java.util.UUID;

@Schema(description = "후보 일괄 처리 요청")
public record BatchCandidateRequest(
    @Schema(description = "변경할 후보 목록 (최대 50개)")
    @NotEmpty List<CandidateUpdateItem> candidates
) {
    @Schema(description = "개별 후보 변경 항목")
    public record CandidateUpdateItem(
        @Schema(description = "후보 고유 ID")
        @NotNull UUID candidateId,
        @Schema(description = "변경할 상태", example = "accepted",
            allowableValues = {"accepted", "rejected"})
        @NotNull CandidateStatus status
    ) {}
}
