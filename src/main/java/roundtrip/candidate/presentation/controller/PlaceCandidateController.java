package roundtrip.candidate.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.candidate.application.PlaceCandidateService;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Place Candidates", description = "장소 후보 — 분석 결과 확인 및 수락/거절")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class PlaceCandidateController {

    private final PlaceCandidateService placeCandidateService;

    @Operation(summary = "장소 후보 목록 조회",
        description = "분석 완료된 잡의 장소 후보 목록을 반환한다. 원본 링크 정보(source_link)와 추출된 후보 배열을 포함한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND — 잡 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
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

    @Operation(summary = "후보 단건 수락/거절/수정",
        description = "status: accepted(수락) | rejected(거절) | edited(사용자가 직접 수정)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — status 누락",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND — 후보 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/candidates/{candidateId}")
    public ResponseEntity<CandidateResponse> updateCandidate(
            @PathVariable UUID candidateId,
            @Valid @RequestBody UpdateCandidateRequest request) {
        PlaceCandidate candidate = placeCandidateService.updateCandidate(candidateId, request.status());
        return ApiResponse.of(SuccessCode.CANDIDATE_UPDATED, CandidateResponse.from(candidate, null));
    }

    @Operation(summary = "후보 일괄 수락/거절",
        description = "최대 50개 후보를 한 번의 요청으로 처리한다. 부분 실패 시에도 성공 항목은 반영되고 실패 목록을 함께 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 완료 (부분 실패 포함)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — candidates 배열이 비었거나 50개 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
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
