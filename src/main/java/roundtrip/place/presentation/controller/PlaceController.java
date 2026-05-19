package roundtrip.place.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.place.application.PlaceService;
import roundtrip.place.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Places", description = "장소 -- 상세 조회, 검색, 유사 추천, 둘러보기, 출처 영상")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class PlaceController {

    private final PlaceService placeService;

    @Operation(summary = "장소 수동 검색",
        description = "저신뢰 후보 대체 또는 직접 추가 시 사용. Kakao / Google Maps API를 통해 검색한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검색 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR -- q 누락",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/places/search")
    public ResponseEntity<PlaceSearchResponse> search(
            @Parameter(description = "검색 키워드", required = true, example = "블루보틀 시부야")
            @RequestParam String q,
            @Parameter(description = "지도 제공자 (kakao / google)", example = "kakao")
            @RequestParam(defaultValue = "kakao") String provider) {
        List<PlaceSearchResult> results = placeService.searchPlaces(q, provider).stream()
                .map(PlaceSearchResult::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_SEARCH_FETCHED, new PlaceSearchResponse(results));
    }

    @Operation(summary = "유사 장소 추천",
        description = "기준 장소의 embedding과 pgvector 코사인 유사도를 기반으로 유사한 장소를 반환한다. Planning Agent `search_similar_places` 도구가 내부적으로 호출한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND -- 기준 장소 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/places/similar")
    public ResponseEntity<PlaceSimilarResponse> similar(
            @Parameter(description = "기준 장소 ID", required = true)
            @RequestParam("place_id") UUID placeId,
            @Parameter(description = "반환 개수 (기본 10, 최대 20)")
            @RequestParam(defaultValue = "10") int limit) {
        int clampedLimit = Math.min(limit, 20);
        List<PlaceSimilarResult> results = placeService.getSimilarPlaces(placeId, clampedLimit).stream()
                .map(PlaceSimilarResult::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_SIMILAR_FETCHED, new PlaceSimilarResponse(results));
    }

    @Operation(summary = "장소 상세 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/places/{placeId}")
    public ResponseEntity<PlaceDetailResponse> getPlace(@PathVariable UUID placeId) {
        PlaceService.PlaceDetailResult result = placeService.getPlace(placeId);
        return ApiResponse.of(SuccessCode.PLACE_FETCHED, PlaceDetailResponse.from(result));
    }

    @Operation(summary = "장소 출처 영상 목록 조회",
        description = "해당 장소가 등장한 원본 영상 목록을 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/places/{placeId}/source-links")
    public ResponseEntity<PlaceSourceLinkListResponse> getSourceLinks(@PathVariable UUID placeId) {
        List<PlaceSourceLinkItem> items = placeService.getSourceLinks(placeId).stream()
                .map(PlaceSourceLinkItem::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_SOURCE_LINK_LIST_FETCHED, new PlaceSourceLinkListResponse(items));
    }

    @Operation(summary = "둘러보기 -- 취향 기반 장소 추천",
        description = "최근 저장 장소의 embedding 평균으로 취향 벡터를 생성하고 pgvector로 유사 장소를 검색한다. "
            + "저장 장소 3개 이하인 경우 카테고리 인기순 fallback을 적용한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/discover")
    public ResponseEntity<DiscoverResponse> discover(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "반환 개수 (기본 20)")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "카테고리 필터 (관광명소 / 맛집 / 카페 / 숙박 / 자연 / 기타)")
            @RequestParam(required = false) String category,
            @Parameter(description = "국가 필터 (ISO 3166-1 alpha-2, 예: KR / JP / TH)")
            @RequestParam(required = false) String countryCode) {
        List<DiscoverResult> results = placeService.getDiscover(user.userId(), limit, category, countryCode).stream()
                .map(DiscoverResult::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_DISCOVER_FETCHED, new DiscoverResponse(results));
    }
}
