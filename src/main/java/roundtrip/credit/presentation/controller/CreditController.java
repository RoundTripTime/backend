package roundtrip.credit.presentation.controller;

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
import roundtrip.credit.application.AdSessionService;
import roundtrip.credit.application.CreditService;
import roundtrip.credit.presentation.dto.*;
import roundtrip.market.domain.entity.CreditHistory;

import java.util.List;
import java.util.UUID;

@Tag(name = "Credits", description = "크레딧 - 내 크레딧 잔액 조회, 적립/차감 내역, 광고 시청")
@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class CreditController {
    private final CreditService creditService;
    private final AdSessionService adSessionService;

    @Operation(summary = "내 크레딧 잔액 조회",
        description = "인증된 사용자의 현재 크레딧 잔액과 가입 이후 누적 적립/차감 총합을 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<CreditBalanceResponse> getMyCredit(@AuthenticationPrincipal AuthenticatedUser principal){
        CreditService.MyCreditResult result = creditService.getMyCredit(principal.userId());
        return ApiResponse.of(SuccessCode.CREDIT_BALANCE_FETCHED,
            CreditBalanceResponse.from(result));
    }

    @Operation(summary = "크레딧 적립/차감 내역 조회",
        description = "내 크레딧 적립/차감 내역을 최신순으로 커서 기반 페이지네이션 조회한다. type으로 적립(earned)/차감(spent) 필터 가능.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me/history")
    public ResponseEntity<CreditHistoryListResponse> getMyHistory(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @Parameter(description = "내역 종류 필터 (earned / spent)")
        @RequestParam(required = false) String type,
        @Parameter(description = "페이지 크기 (기본 20, 최대 50)")
        @RequestParam(defaultValue = "20") int limit,
        @Parameter(description = "페이지네이션 커서 (이전 응답의 next_cursor)")
        @RequestParam(required = false) UUID cursor
        ){
        String normalizedType = ("earned".equals(type) || "spent".equals(type)) ? type : null;
        int pageSize = Math.max(1, Math.min(limit, 50));

        List<CreditHistory> items = creditService.getMyHistory(principal.userId(), normalizedType, pageSize, cursor);
        UUID nextCursor = items.size() == pageSize ? items.get(items.size() - 1).getId() : null;
        List<CreditHistoryItem> dtoItems = items.stream().map(CreditHistoryItem::from).toList();

        return ApiResponse.of(SuccessCode.CREDIT_HISTORY_FETCHED,
            new CreditHistoryListResponse(dtoItems, nextCursor));
    }

    @Operation(summary = "광고 시청 세션 시작",
        description = "광고 시청을 위한 세션을 생성한다. 광고 5회 시청 시 크레딧 1개 적립. 일일 시청 한도 초과 시 422.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "세션 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "AD_LIMIT_REACHED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/ads/start")
    public ResponseEntity<AdSessionStartResponse> startAd(@AuthenticationPrincipal AuthenticatedUser principal){
        AdSessionService.StartResult result = adSessionService.start(principal.userId());
        return ApiResponse.of(SuccessCode.AD_SESSION_STARTED, AdSessionStartResponse.from(result));
    }


    @Operation(summary = "광고 시청 완료",
        description = "광고 시청 완료를 기록한다. 오늘 누적 시청 5의 배수 도달 시 크레딧 1개 자동 적립.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "완료 처리 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "AD_SESSION_NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "AD_ALREADY_COMPLETED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "INVALID_AD_SESSION",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/ads/complete")
    public ResponseEntity<AdSessionCompleteResponse> completeAd(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody AdSessionCompleteRequest request){
        AdSessionService.CompleteResult result = adSessionService.complete(request.adSessionId(), principal.userId());
        return ApiResponse.of(SuccessCode.AD_VIEW_COMPLETED, AdSessionCompleteResponse.from(result));
    }
}
