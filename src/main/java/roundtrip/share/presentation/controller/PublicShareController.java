package roundtrip.share.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import roundtrip.collection.domain.entity.Collection;
import roundtrip.collection.domain.repository.CollectionRepository;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;
import roundtrip.itinerary.domain.repository.ItineraryRepository;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.share.presentation.dto.PublicCollectionResponse;
import roundtrip.share.presentation.dto.PublicItineraryResponse;
import roundtrip.share.presentation.dto.PublicItineraryResponse.ItemWithPlace;

import java.util.List;

@Tag(name = "Public Share", description = "공개 공유 — 비인증 접근 허용, 읽기 전용")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicShareController {

    private final ItineraryRepository itineraryRepository;
    private final CollectionRepository collectionRepository;
    private final PlaceRepository placeRepository;

    @Operation(summary = "공유된 플랜 조회", description = "share_token으로 플랜을 조회합니다. 비인증 접근 허용.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/itineraries/{shareToken}")
    public ResponseEntity<PublicItineraryResponse> getSharedItinerary(@PathVariable String shareToken) {
        Itinerary itinerary = itineraryRepository.findByShareToken(shareToken)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_TOKEN_NOT_FOUND));

        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(itinerary.getId());
        List<ItemWithPlace> itemsWithPlaces = items.stream()
            .map(item -> {
                Place place = placeRepository.findById(item.getPlaceId()).orElse(null);
                return new ItemWithPlace(item, place);
            })
            .filter(iwp -> iwp.place() != null)
            .toList();

        return ApiResponse.of(SuccessCode.PUBLIC_ITINERARY_FETCHED,
            PublicItineraryResponse.from(itinerary, itemsWithPlaces));
    }

    @Operation(summary = "공유된 플레이스 조회", description = "share_token으로 플레이스를 조회합니다. 비인증 접근 허용.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/collections/{shareToken}")
    public ResponseEntity<PublicCollectionResponse> getSharedCollection(@PathVariable String shareToken) {
        Collection collection = collectionRepository.findByShareToken(shareToken)
            .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_TOKEN_NOT_FOUND));

        List<Place> places = collectionRepository.findPlacesByCollectionId(collection.getId());
        return ApiResponse.of(SuccessCode.PUBLIC_COLLECTION_FETCHED,
            PublicCollectionResponse.from(collection, places));
    }
}
