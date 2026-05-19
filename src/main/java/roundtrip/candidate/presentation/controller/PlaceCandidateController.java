package roundtrip.candidate.presentation.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.candidate.application.PlaceCandidateService;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class PlaceCandidateController {

    private final PlaceCandidateService placeCandidateService;

    @GetMapping("/jobs/{jobId}/candidates")
    public ResponseEntity<CandidateListResponse> getCandidatesByJob(@PathVariable UUID jobId) {
        PlaceCandidateService.CandidateListResult result = placeCandidateService.getCandidatesByJob(jobId);

        List<CandidateResponse> candidateResponses = result.candidates().stream()
                .map(CandidateResponse::from)
                .toList();

        CandidateListResponse response = new CandidateListResponse(
                SourceLinkSummary.from(result.sourceLink()),
                candidateResponses
        );

        return ApiResponse.of(SuccessCode.CANDIDATE_LIST_FETCHED, response);
    }

    @PatchMapping("/candidates/{candidateId}")
    public ResponseEntity<CandidateResponse> updateCandidate(
            @PathVariable UUID candidateId,
            @Valid @RequestBody UpdateCandidateRequest request) {
        PlaceCandidate candidate = placeCandidateService.updateCandidate(candidateId, request.status());
        return ApiResponse.of(SuccessCode.CANDIDATE_UPDATED, CandidateResponse.from(candidate, null));
    }

    @PostMapping("/candidates/batch")
    public ResponseEntity<BatchCandidateResponse> batchUpdate(
            @Valid @RequestBody BatchCandidateRequest request) {
        List<PlaceCandidateService.BatchUpdateRequest> batchRequests = request.candidates().stream()
                .map(item -> new PlaceCandidateService.BatchUpdateRequest(item.candidateId(), item.status()))
                .toList();

        PlaceCandidateService.BatchUpdateResult result = placeCandidateService.batchUpdateCandidates(batchRequests);
        return ApiResponse.of(SuccessCode.CANDIDATE_BATCH_PROCESSED, BatchCandidateResponse.from(result));
    }
}
