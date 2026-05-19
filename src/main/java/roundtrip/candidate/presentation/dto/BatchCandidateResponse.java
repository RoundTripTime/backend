package roundtrip.candidate.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.candidate.application.PlaceCandidateService;

import java.util.List;
import java.util.UUID;

@Schema(description = "후보 일괄 처리 응답")
public record BatchCandidateResponse(
    @Schema(description = "성공적으로 상태가 변경된 후보 ID 목록")
    List<UUID> updated,
    @Schema(description = "처리 실패한 후보 목록")
    List<FailedItem> failed
) {
    @Schema(description = "처리 실패 항목")
    public record FailedItem(
        @Schema(description = "후보 고유 ID")
        UUID candidateId,
        @Schema(description = "실패 사유", example = "NOT_FOUND")
        String reason
    ) {}

    public static BatchCandidateResponse from(PlaceCandidateService.BatchUpdateResult result) {
        List<FailedItem> failedItems = result.failed().stream()
                .map(f -> new FailedItem(f.candidateId(), f.reason()))
                .toList();
        return new BatchCandidateResponse(result.updated(), failedItems);
    }
}
