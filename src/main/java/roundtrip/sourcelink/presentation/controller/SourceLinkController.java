package roundtrip.sourcelink.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.sourcelink.application.SourceLinkService;
import roundtrip.sourcelink.domain.entity.LinkStatus;
import roundtrip.sourcelink.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Source Links", description = "링크 수집 -- 공유받은 URL 제출 및 목록 조회")
@RestController
@RequestMapping("/source-links")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class SourceLinkController {

    private final SourceLinkService sourceLinkService;

    @Operation(summary = "링크 제출 + 분석 잡 생성",
        description = "공유하기로 수신된 URL을 제출하고 분석 잡을 생성한다. 반환된 job_id로 `/jobs/:job_id`를 폴링하여 상태를 확인한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제출 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR -- url 누락",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DUPLICATE_LINK -- 이미 처리 중인 동일 링크",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "UNSUPPORTED_PLATFORM -- 지원하지 않는 URL 플랫폼",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<SubmitSourceLinkResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SubmitSourceLinkRequest request) {
        SourceLinkService.SubmitResult result = sourceLinkService.submit(principal.userId(), request.url());
        return ApiResponse.of(SuccessCode.SOURCE_LINK_SUBMITTED, SubmitSourceLinkResponse.from(result));
    }

    @Operation(summary = "링크 목록 조회", description = "제출한 링크 목록을 커서 기반 페이지네이션으로 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<SourceLinkListResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Parameter(description = "상태 필터 (pending / processing / done / failed)")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 크기 (기본 20, 최대 50)")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "페이지네이션 커서 (이전 응답의 next_cursor)")
            @RequestParam(required = false) UUID cursor) {
        LinkStatus linkStatus = null;
        if (status != null) {
            try {
                linkStatus = LinkStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<SourceLinkService.SourceLinkWithJob> items =
                sourceLinkService.listByUser(principal.userId(), linkStatus, limit, cursor);

        UUID nextCursor = null;
        if (items.size() == limit) {
            nextCursor = items.get(items.size() - 1).sourceLink().getId();
        }

        List<SourceLinkItem> dtoItems = items.stream()
                .map(r -> SourceLinkItem.of(r.sourceLink(), r.jobStatus()))
                .toList();
        return ApiResponse.of(SuccessCode.SOURCE_LINK_LIST_FETCHED, new SourceLinkListResponse(dtoItems, nextCursor));
    }
}
