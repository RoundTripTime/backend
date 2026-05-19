package roundtrip.collection.presentation.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.collection.application.CollectionService;
import roundtrip.collection.presentation.dto.*;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class CollectionController {

    private final CollectionService collectionService;

    @GetMapping("/collections")
    public ResponseEntity<CollectionListResponse> getCollections(
            @AuthenticationPrincipal AuthenticatedUser user) {
        var summaries = collectionService.getCollections(user.userId());
        var items = summaries.stream().map(CollectionItem::from).toList();
        return ApiResponse.of(SuccessCode.COLLECTION_LIST_FETCHED, new CollectionListResponse(items));
    }

    @PostMapping("/collections")
    public ResponseEntity<CollectionResponse> createCollection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateCollectionRequest request) {
        var collection = collectionService.createCollection(user.userId(), request.name(), request.icon());
        return ApiResponse.of(SuccessCode.COLLECTION_CREATED, CollectionResponse.from(collection));
    }

    @PatchMapping("/collections/{collectionId}")
    public ResponseEntity<CollectionResponse> updateCollection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId,
            @RequestBody UpdateCollectionRequest request) {
        var collection = collectionService.updateCollection(
                user.userId(), collectionId, request.name(), request.icon(), request.visibility());
        return ApiResponse.of(SuccessCode.COLLECTION_UPDATED, CollectionResponse.from(collection));
    }

    @DeleteMapping("/collections/{collectionId}")
    public ResponseEntity<Void> deleteCollection(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId) {
        collectionService.deleteCollection(user.userId(), collectionId);
        return ApiResponse.noContent(SuccessCode.COLLECTION_DELETED);
    }

    @GetMapping("/collections/{collectionId}/places")
    public ResponseEntity<CollectionPlaceListResponse> getCollectionPlaces(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId) {
        var result = collectionService.getCollectionPlaces(user.userId(), collectionId);
        return ApiResponse.of(SuccessCode.COLLECTION_PLACE_LIST_FETCHED, CollectionPlaceListResponse.from(result));
    }

    @PostMapping("/collections/{collectionId}/places")
    public ResponseEntity<Void> addPlace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId,
            @Valid @RequestBody AddPlaceToCollectionRequest request) {
        collectionService.addPlace(user.userId(), collectionId, request.placeId());
        return ApiResponse.of(SuccessCode.COLLECTION_PLACE_ADDED, null);
    }

    @DeleteMapping("/collections/{collectionId}/places/{placeId}")
    public ResponseEntity<Void> removePlace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId,
            @PathVariable UUID placeId) {
        collectionService.removePlace(user.userId(), collectionId, placeId);
        return ApiResponse.noContent(SuccessCode.COLLECTION_PLACE_REMOVED);
    }

    @GetMapping("/collections/{collectionId}/share")
    public ResponseEntity<CollectionShareResponse> getShareLink(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID collectionId) {
        CollectionService.ShareInfo info = collectionService.getShareLink(user.userId(), collectionId);
        return ApiResponse.of(SuccessCode.COLLECTION_SHARE_FETCHED,
                new CollectionShareResponse(info.shareUrl(), info.visibility()));
    }
}
