package roundtrip.extract.presentation.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.extract.application.ExtractionJobService;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.extract.presentation.dto.ExtractionJobResponse;

import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class ExtractionJobController {

    private final ExtractionJobService extractionJobService;

    @GetMapping("/{jobId}")
    public ResponseEntity<ExtractionJobResponse> getJob(@PathVariable UUID jobId) {
        ExtractionJob job = extractionJobService.getJob(jobId);
        return ApiResponse.of(SuccessCode.JOB_STATUS_FETCHED, ExtractionJobResponse.from(job));
    }
}
