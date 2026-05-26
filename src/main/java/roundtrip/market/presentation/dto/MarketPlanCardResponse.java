package roundtrip.market.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.market.application.MarketService.MarketPlanCard;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "플랜 마켓 카드 응답")
public record MarketPlanCardResponse(
    @Schema(description = "마켓 플랜 고유 ID") UUID marketPlanId,
    @Schema(description = "마켓 노출 제목") String title,
    @Schema(description = "여행지") String destinationRegion,
    @Schema(description = "숙박 일수") int durationNights,
    @Schema(description = "여행 인원 수") int partySize,
    @Schema(description = "열람 크레딧 수") int creditPrice,
    @Schema(description = "등록자 정보") AuthorInfo author,
    @Schema(description = "카드 대표 썸네일 URL", nullable = true) String coverThumbnailUrl,
    @Schema(description = "한 줄 소개") String highlight,
    @Schema(description = "포함된 장소 수") int placeCount,
    @Schema(description = "누적 조회수") int viewCount,
    @Schema(description = "OTA 예약 완료 여부") boolean isOtaVerified,
    @Schema(description = "등록 일시") OffsetDateTime createdAt
) {
    public static MarketPlanCardResponse from(MarketPlanCard card) {
        var plan = card.plan();
        var itinerary = card.itinerary();
        return new MarketPlanCardResponse(
            plan.getId(),
            plan.getTitle(),
            itinerary != null ? itinerary.getDestinationRegion() : null,
            card.durationNights(),
            itinerary != null ? itinerary.getPartySize() : 0,
            plan.getCreditPrice(),
            AuthorInfo.from(card.author()),
            card.coverThumbnailUrl(),
            plan.getHighlight(),
            card.placeCount(),
            plan.getViewCount(),
            plan.isOtaVerified(),
            plan.getCreatedAt()
        );
    }
}
