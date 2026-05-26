package roundtrip.market.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import roundtrip.market.application.MarketService;
import roundtrip.market.domain.entity.MarketPlan;
import roundtrip.market.presentation.dto.*;

import java.util.UUID;

@Tag(name = "Plan Market", description = "플랜 마켓 — 등록, 조회, 미리보기, 열람, 취소")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class MarketController {

    private final MarketService marketService;

    @Operation(summary = "플랜 마켓 목록 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/market/plans")
    public ResponseEntity<MarketPlanListResponse> getMarketPlans(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID cursor) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var result = marketService.getMarketPlans(q, sort, cursor, safeLimit);
        return ApiResponse.of(SuccessCode.MARKET_PLAN_LIST_FETCHED, MarketPlanListResponse.from(result));
    }

    @Operation(summary = "플랜 마켓 등록", description = "OTA 예약 완료된 플랜만 등록 가능.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ALREADY_LISTED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/market/plans")
    public ResponseEntity<MarketPlanCardResponse> createMarketPlan(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateMarketPlanRequest request) {
        MarketPlan plan = marketService.createMarketPlan(
            user.userId(), request.itineraryId(), request.title(),
            request.description(), request.highlight(),
            request.pros(), request.cons(), request.tips());

        // 간단히 카드 형식으로 반환
        var listResult = marketService.getMarketPlans(null, "latest", null, 1);
        var card = listResult.items().stream()
            .filter(c -> c.plan().getId().equals(plan.getId()))
            .findFirst()
            .orElse(null);

        if (card != null) {
            return ApiResponse.of(SuccessCode.MARKET_PLAN_LISTED, MarketPlanCardResponse.from(card));
        }
        return ApiResponse.of(SuccessCode.MARKET_PLAN_LISTED, null);
    }

    @Operation(summary = "플랜 마켓 미리보기", description = "크레딧 차감 없이 제목/소개/일부 장소만 반환.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/market/plans/{marketPlanId}/preview")
    public ResponseEntity<MarketPlanPreviewResponse> getPreview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID marketPlanId) {
        var result = marketService.getPreview(marketPlanId, user.userId());
        return ApiResponse.of(SuccessCode.MARKET_PLAN_PREVIEW_FETCHED, MarketPlanPreviewResponse.from(result));
    }

    @Operation(summary = "플랜 마켓 상세 조회", description = "최초 조회 시 크레딧 1개 차감. 이미 구매한 경우 차감 없이 반환.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "402", description = "INSUFFICIENT_CREDITS",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/market/plans/{marketPlanId}")
    public ResponseEntity<MarketPlanDetailResponse> getDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID marketPlanId) {
        var result = marketService.getDetail(marketPlanId, user.userId());
        return ApiResponse.of(SuccessCode.MARKET_PLAN_FETCHED, MarketPlanDetailResponse.from(result));
    }

    @Operation(summary = "플랜 마켓 등록 취소", description = "본인 등록 플랜만 가능.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/market/plans/{marketPlanId}")
    public ResponseEntity<Void> unlistPlan(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID marketPlanId) {
        marketService.unlistPlan(marketPlanId, user.userId());
        return ApiResponse.noContent(SuccessCode.MARKET_PLAN_UNLISTED);
    }
}
