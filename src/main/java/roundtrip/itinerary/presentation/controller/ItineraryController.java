package roundtrip.itinerary.presentation.controller;

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
import roundtrip.itinerary.application.ItineraryService;
import roundtrip.itinerary.presentation.dto.*;
import roundtrip.place.domain.repository.PlaceRepository;

import java.util.List;
import java.util.UUID;

@Tag(name = "Itineraries", description = "플랜 — 여행 일정 관리")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class ItineraryController {

    private final ItineraryService itineraryService;
    private final PlaceRepository placeRepository;

    @Operation(summary = "내 플랜 목록 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/itineraries")
    public ResponseEntity<ItineraryListResponse> getItineraries(
            @AuthenticationPrincipal AuthenticatedUser user) {
        var summaries = itineraryService.getItineraries(user.userId());
        var items = summaries.stream()
                .map(s -> ItineraryListResponse.ItineraryItem.from(s.itinerary(), s.placeCount()))
                .toList();
        return ApiResponse.of(SuccessCode.ITINERARY_LIST_FETCHED, new ItineraryListResponse(items));
    }

    @Operation(summary = "플랜 생성")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/itineraries")
    public ResponseEntity<ItineraryResponse> createItinerary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateItineraryRequest request) {
        var itinerary = itineraryService.createItinerary(
                user.userId(), request.title(), request.destinationRegion(),
                request.startDate(), request.endDate(), request.partySize());
        return ApiResponse.of(SuccessCode.ITINERARY_CREATED,
                ItineraryResponse.from(itinerary, List.of()));
    }

    @Operation(summary = "플랜 상세 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/itineraries/{itineraryId}")
    public ResponseEntity<ItineraryResponse> getItinerary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId) {
        var result = itineraryService.getItinerary(user.userId(), itineraryId);
        var itemResponses = result.items().stream()
                .map(item -> ItineraryItemResponse.from(item,
                        result.placeNames().getOrDefault(item.getPlaceId(), "")))
                .toList();
        return ApiResponse.of(SuccessCode.ITINERARY_FETCHED,
                ItineraryResponse.from(result.itinerary(), itemResponses));
    }

    @Operation(summary = "플랜 수정", description = "변경할 항목만 포함 — 모든 필드 optional.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/itineraries/{itineraryId}")
    public ResponseEntity<ItineraryResponse> updateItinerary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId,
            @RequestBody UpdateItineraryRequest request) {
        var itinerary = itineraryService.updateItinerary(
                user.userId(), itineraryId,
                request.title(), request.destinationRegion(),
                request.startDate(), request.endDate(),
                request.partySize(), request.visibility(), request.status());
        var result = itineraryService.getItinerary(user.userId(), itineraryId);
        var itemResponses = result.items().stream()
                .map(item -> ItineraryItemResponse.from(item,
                        result.placeNames().getOrDefault(item.getPlaceId(), "")))
                .toList();
        return ApiResponse.of(SuccessCode.ITINERARY_UPDATED,
                ItineraryResponse.from(itinerary, itemResponses));
    }

    @Operation(summary = "플랜 삭제")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/itineraries/{itineraryId}")
    public ResponseEntity<Void> deleteItinerary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId) {
        itineraryService.deleteItinerary(user.userId(), itineraryId);
        return ApiResponse.noContent(SuccessCode.ITINERARY_DELETED);
    }

    @Operation(summary = "플랜에 장소 추가")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND — 플랜 또는 장소 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/itineraries/{itineraryId}/items")
    public ResponseEntity<ItineraryItemResponse> addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId,
            @Valid @RequestBody AddItineraryItemRequest request) {
        var item = itineraryService.addItem(
                user.userId(), itineraryId, request.placeId(),
                request.dayIndex(), request.sortOrder(), request.plannedDurationMinutes());
        String placeName = placeRepository.findById(item.getPlaceId())
                .map(p -> p.getCanonicalName()).orElse("");
        return ApiResponse.of(SuccessCode.ITINERARY_ITEM_ADDED,
                ItineraryItemResponse.from(item, placeName));
    }

    @Operation(summary = "장소 일정 수정")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/itineraries/{itineraryId}/items/{itemId}")
    public ResponseEntity<ItineraryItemResponse> updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId,
            @PathVariable UUID itemId,
            @RequestBody UpdateItineraryItemRequest request) {
        var item = itineraryService.updateItem(
                user.userId(), itineraryId, itemId,
                request.dayIndex(), request.sortOrder(), request.plannedDurationMinutes());
        String placeName = placeRepository.findById(item.getPlaceId())
                .map(p -> p.getCanonicalName()).orElse("");
        return ApiResponse.of(SuccessCode.ITINERARY_ITEM_UPDATED,
                ItineraryItemResponse.from(item, placeName));
    }

    @Operation(summary = "플랜에서 장소 제거")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "제거 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/itineraries/{itineraryId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId,
            @PathVariable UUID itemId) {
        itineraryService.removeItem(user.userId(), itineraryId, itemId);
        return ApiResponse.noContent(SuccessCode.ITINERARY_ITEM_REMOVED);
    }

    @Operation(summary = "일정 순서 일괄 변경")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "순서 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/itineraries/{itineraryId}/items/reorder")
    public ResponseEntity<Void> reorderItems(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId,
            @Valid @RequestBody ReorderItemsRequest request) {
        itineraryService.reorderItems(user.userId(), itineraryId, request.items());
        return ApiResponse.of(SuccessCode.ITINERARY_ITEM_REORDERED, null);
    }

    @Operation(summary = "플랜 공유 링크 조회/생성")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/itineraries/{itineraryId}/share")
    public ResponseEntity<ItineraryShareResponse> getShareLink(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId) {
        var info = itineraryService.getShareLink(user.userId(), itineraryId);
        return ApiResponse.of(SuccessCode.ITINERARY_SHARE_FETCHED,
                new ItineraryShareResponse(info.shareUrl(), info.visibility()));
    }

    @Operation(summary = "OTA 예약 링크 생성")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "링크 생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/itineraries/{itineraryId}/ota-links")
    public ResponseEntity<OtaLinkResponse> getOtaLinks(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID itineraryId,
            @RequestParam String type) {
        var result = itineraryService.generateOtaLink(user.userId(), itineraryId, type);
        return ApiResponse.of(SuccessCode.ITINERARY_OTA_LINK_GENERATED, new OtaLinkResponse(result.otaUrl()));
    }
}
