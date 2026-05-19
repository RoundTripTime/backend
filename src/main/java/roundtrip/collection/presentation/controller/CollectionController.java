package roundtrip.collection.presentation.controller;

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
import roundtrip.collection.application.CollectionService;
import roundtrip.collection.presentation.dto.*;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;

import java.util.UUID;

@Tag(name = "Collections", description = "플레이스 — 장소를 지역/주제별로 묶는 사용자 정의 컬렉션")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class CollectionController {

    private final CollectionService collectionService;

    @Operation(summary = "내 플레이스 목록 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/collections")
    public ResponseEntity<CollectionListResponse> getCollections(
            @AuthenticationPrincipal AuthenticatedUser user) {
        var summaries = collectionService.getCollections(user.userId());
        var items = summaries.stream().map(CollectionItem::from).toList();
        return ApiResponse.of(SuccessCode.COLLECTION_LIST_FETCHED, new CollectionListResponse(items));
    }

    @Operation(summary = "플레이스 생성")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — name 누락 또는 100자 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/collections")
    public ResponseEntity<CollectionResponse> createCollection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateCollectionRequest request) {
        var collection = collectionService.createCollection(user.userId(), request.name(), request.icon());
        return ApiResponse.of(SuccessCode.COLLECTION_CREATED, CollectionResponse.from(collection));
    }

    @Operation(summary = "플레이스 수정", description = "변경할 항목만 포함 — 모든 필드 optional.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN — 타인 플레이스 수정 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/collections/{collectionId}")
    public ResponseEntity<CollectionResponse> updateCollection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId,
            @RequestBody UpdateCollectionRequest request) {
        var collection = collectionService.updateCollection(
                user.userId(), collectionId, request.name(), request.icon(), request.visibility());
        return ApiResponse.of(SuccessCode.COLLECTION_UPDATED, CollectionResponse.from(collection));
    }

    @Operation(summary = "플레이스 삭제", description = "is_default: true인 기본 플레이스는 삭제 불가 (403 반환).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN — 기본 플레이스 삭제 시도 또는 타인 플레이스",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/collections/{collectionId}")
    public ResponseEntity<Void> deleteCollection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId) {
        collectionService.deleteCollection(user.userId(), collectionId);
        return ApiResponse.noContent(SuccessCode.COLLECTION_DELETED);
    }

    @Operation(summary = "플레이스 내 장소 목록 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/collections/{collectionId}/places")
    public ResponseEntity<CollectionPlaceListResponse> getCollectionPlaces(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId) {
        var result = collectionService.getCollectionPlaces(user.userId(), collectionId);
        return ApiResponse.of(SuccessCode.COLLECTION_PLACE_LIST_FETCHED, CollectionPlaceListResponse.from(result));
    }

    @Operation(summary = "플레이스에 장소 추가")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "추가 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — place_id 누락",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND — 플레이스 또는 장소 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/collections/{collectionId}/places")
    public ResponseEntity<Void> addPlace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId,
            @Valid @RequestBody AddPlaceToCollectionRequest request) {
        collectionService.addPlace(user.userId(), collectionId, request.placeId());
        return ApiResponse.of(SuccessCode.COLLECTION_PLACE_ADDED, null);
    }

    @Operation(summary = "플레이스에서 장소 제거")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "제거 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/collections/{collectionId}/places/{placeId}")
    public ResponseEntity<Void> removePlace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId,
            @PathVariable UUID placeId) {
        collectionService.removePlace(user.userId(), collectionId, placeId);
        return ApiResponse.noContent(SuccessCode.COLLECTION_PLACE_REMOVED);
    }

    @Operation(summary = "플레이스 공유 링크 조회/생성")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/collections/{collectionId}/share")
    public ResponseEntity<CollectionShareResponse> getShareLink(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId) {
        CollectionService.ShareInfo info = collectionService.getShareLink(user.userId(), collectionId);
        return ApiResponse.of(SuccessCode.COLLECTION_SHARE_FETCHED,
                new CollectionShareResponse(info.shareUrl(), info.visibility()));
    }
}
