package roundtrip.extract.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.extract.application.ExtractionJobService;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.extract.presentation.dto.ExtractionJobResponse;

import java.util.UUID;

@Tag(name = "Extraction Jobs", description = "분석 잡 — 링크 분석 상태 조회")
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class ExtractionJobController {

    private final ExtractionJobService extractionJobService;

    @Operation(summary = "분석 잡 상태 조회",
        description = "링크 분석 잡의 현재 상태를 반환한다. 상태 전이: pending -> processing -> done | failed")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND — 잡 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{jobId}")
    public ResponseEntity<ExtractionJobResponse> getJob(@PathVariable UUID jobId) {
        ExtractionJob job = extractionJobService.getJob(jobId);
        return ApiResponse.of(SuccessCode.JOB_STATUS_FETCHED, ExtractionJobResponse.from(job));
    }
}
