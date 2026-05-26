package roundtrip.market.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.market.application.MarketService.PreviewResult;
import roundtrip.place.domain.entity.Place;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Schema(description = "플랜 마켓 미리보기 응답")
public record MarketPlanPreviewResponse(
    @Schema(description = "마켓 플랜 고유 ID") UUID marketPlanId,
    @Schema(description = "마켓 노출 제목") String title,
    @Schema(description = "여행지") String destinationRegion,
    @Schema(description = "숙박 일수") int durationNights,
    @Schema(description = "여행 인원 수") int partySize,
    @Schema(description = "열람 크레딧 수") int creditPrice,
    @Schema(description = "등록자 정보") AuthorInfo author,
    @Schema(description = "플랜 소개") String description,
    @Schema(description = "한 줄 소개") String highlight,
    @Schema(description = "미리보기 장소 목록") List<PreviewPlaceInfo> previewPlaces,
    @Schema(description = "크레딧 차감 후 열람 가능한 장소 수") int hiddenPlaceCount,
    @Schema(description = "누적 조회수") int viewCount,
    @Schema(description = "현재 사용자의 구매 여부") boolean isPurchased
) {
    public static MarketPlanPreviewResponse from(PreviewResult result) {
        var plan = result.plan();
        var itinerary = result.itinerary();

        int durationNights = 0;
        if (itinerary.getStartDate() != null && itinerary.getEndDate() != null) {
            durationNights = (int) ChronoUnit.DAYS.between(itinerary.getStartDate(), itinerary.getEndDate());
        }

        List<PreviewPlaceInfo> previewPlaces = result.previewPlace() != null
            ? List.of(PreviewPlaceInfo.from(result.previewPlace()))
            : List.of();

        return new MarketPlanPreviewResponse(
            plan.getId(), plan.getTitle(), itinerary.getDestinationRegion(),
            durationNights, itinerary.getPartySize(), plan.getCreditPrice(),
            AuthorInfo.from(result.author()),
            plan.getDescription(), plan.getHighlight(),
            previewPlaces, result.hiddenPlaceCount(),
            plan.getViewCount(), result.isPurchased()
        );
    }

    @Schema(description = "미리보기 장소 정보")
    public record PreviewPlaceInfo(
        @Schema(description = "장소 고유 ID") UUID placeId,
        @Schema(description = "정규화된 장소명") String canonicalName,
        @Schema(description = "장소 카테고리") String category,
        @Schema(description = "장소 썸네일 URL", nullable = true) String thumbnailUrl
    ) {
        public static PreviewPlaceInfo from(Place place) {
            return new PreviewPlaceInfo(
                place.getId(), place.getCanonicalName(),
                place.getCategory() != null ? place.getCategory().name().toLowerCase() : null,
                place.getThumbnailUrl()
            );
        }
    }
}
