package roundtrip.place.presentation.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.place.application.PlaceService;
import roundtrip.place.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping("/places/search")
    public ResponseEntity<PlaceSearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "kakao") String provider) {
        List<PlaceSearchResult> results = placeService.searchPlaces(q, provider).stream()
                .map(PlaceSearchResult::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_SEARCH_FETCHED, new PlaceSearchResponse(results));
    }

    @GetMapping("/places/similar")
    public ResponseEntity<PlaceSimilarResponse> similar(
            @RequestParam("place_id") UUID placeId,
            @RequestParam(defaultValue = "10") int limit) {
        int clampedLimit = Math.min(limit, 20);
        List<PlaceSimilarResult> results = placeService.getSimilarPlaces(placeId, clampedLimit).stream()
                .map(PlaceSimilarResult::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_SIMILAR_FETCHED, new PlaceSimilarResponse(results));
    }

    @GetMapping("/places/{placeId}")
    public ResponseEntity<PlaceDetailResponse> getPlace(@PathVariable UUID placeId) {
        PlaceService.PlaceDetailResult result = placeService.getPlace(placeId);
        return ApiResponse.of(SuccessCode.PLACE_FETCHED, PlaceDetailResponse.from(result));
    }

    @GetMapping("/places/{placeId}/source-links")
    public ResponseEntity<PlaceSourceLinkListResponse> getSourceLinks(@PathVariable UUID placeId) {
        List<PlaceSourceLinkItem> items = placeService.getSourceLinks(placeId).stream()
                .map(PlaceSourceLinkItem::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_SOURCE_LINK_LIST_FETCHED, new PlaceSourceLinkListResponse(items));
    }

    @GetMapping("/discover")
    public ResponseEntity<DiscoverResponse> discover(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String countryCode) {
        List<DiscoverResult> results = placeService.getDiscover(user.userId(), limit, category, countryCode).stream()
                .map(DiscoverResult::from)
                .toList();
        return ApiResponse.of(SuccessCode.PLACE_DISCOVER_FETCHED, new DiscoverResponse(results));
    }
}
