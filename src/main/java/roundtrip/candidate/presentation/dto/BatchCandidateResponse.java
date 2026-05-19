package roundtrip.candidate.presentation.dto;

import roundtrip.candidate.application.PlaceCandidateService;

import java.util.List;
import java.util.UUID;

public record BatchCandidateResponse(
        List<UUID> updated,
        List<FailedItem> failed
) {
    public record FailedItem(UUID candidateId, String reason) {}

    public static BatchCandidateResponse from(PlaceCandidateService.BatchUpdateResult result) {
        List<FailedItem> failedItems = result.failed().stream()
                .map(f -> new FailedItem(f.candidateId(), f.reason()))
                .toList();
        return new BatchCandidateResponse(result.updated(), failedItems);
    }
}
