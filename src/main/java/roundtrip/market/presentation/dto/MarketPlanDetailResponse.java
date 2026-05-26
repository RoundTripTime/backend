package roundtrip.market.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.market.application.MarketService.DayGroup;
import roundtrip.market.application.MarketService.DayItem;
import roundtrip.market.application.MarketService.DetailResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Schema(description = "플랜 마켓 상세 응답")
public record MarketPlanDetailResponse(
    @Schema(description = "마켓 플랜 고유 ID") UUID marketPlanId,
    @Schema(description = "마켓 노출 제목") String title,
    @Schema(description = "여행지") String destinationRegion,
    @Schema(description = "숙박 일수") int durationNights,
    @Schema(description = "여행 인원 수") int partySize,
    @Schema(description = "등록자 정보") AuthorInfo author,
    @Schema(description = "플랜 소개") String description,
    @Schema(description = "한 줄 소개") String highlight,
    @Schema(description = "좋았던 점") String pros,
    @Schema(description = "아쉬웠던 점") String cons,
    @Schema(description = "추가 팁", nullable = true) String tips,
    @Schema(description = "Day별 일정 목록") List<DayGroupResponse> days,
    @Schema(description = "OTA 예약 정보") OtaBookingInfo otaBookingInfo,
    @Schema(description = "누적 조회수") int viewCount,
    @Schema(description = "등록 일시") OffsetDateTime createdAt
) {
    public static MarketPlanDetailResponse from(DetailResult result) {
        var plan = result.plan();
        var itinerary = result.itinerary();

        int durationNights = 0;
        if (itinerary.getStartDate() != null && itinerary.getEndDate() != null) {
            durationNights = (int) ChronoUnit.DAYS.between(itinerary.getStartDate(), itinerary.getEndDate());
        }

        List<DayGroupResponse> days = result.days().stream()
            .map(DayGroupResponse::from)
            .toList();

        return new MarketPlanDetailResponse(
            plan.getId(), plan.getTitle(), itinerary.getDestinationRegion(),
            durationNights, itinerary.getPartySize(),
            AuthorInfo.from(result.author()),
            plan.getDescription(), plan.getHighlight(),
            plan.getPros(), plan.getCons(), plan.getTips(),
            days,
            new OtaBookingInfo(
                plan.getOtaBookedAt() != null ? plan.getOtaBookedAt().toString() : null,
                plan.isOtaVerified()
            ),
            plan.getViewCount(), plan.getCreatedAt()
        );
    }

    @Schema(description = "Day별 일정")
    public record DayGroupResponse(
        @Schema(description = "여행 일차") int dayIndex,
        @Schema(description = "해당 일차의 장소 목록") List<DayItemResponse> items
    ) {
        public static DayGroupResponse from(DayGroup group) {
            List<DayItemResponse> items = group.items().stream()
                .map(DayItemResponse::from)
                .toList();
            return new DayGroupResponse(group.dayIndex(), items);
        }
    }

    @Schema(description = "일정 장소 아이템")
    public record DayItemResponse(
        @Schema(description = "장소 고유 ID") UUID placeId,
        @Schema(description = "정규화된 장소명") String canonicalName,
        @Schema(description = "장소 카테고리") String category,
        @Schema(description = "위도") BigDecimal latitude,
        @Schema(description = "경도") BigDecimal longitude,
        @Schema(description = "장소 썸네일 URL", nullable = true) String thumbnailUrl,
        @Schema(description = "예상 체류 시간(분)", nullable = true) Integer plannedDurationMinutes,
        @Schema(description = "해당 일차 내 순서", nullable = true) Integer sortOrder
    ) {
        public static DayItemResponse from(DayItem item) {
            var place = item.place();
            return new DayItemResponse(
                place.getId(), place.getCanonicalName(),
                place.getCategory() != null ? place.getCategory().name().toLowerCase() : null,
                place.getLatitude(), place.getLongitude(), place.getThumbnailUrl(),
                item.plannedDurationMinutes(), item.sortOrder()
            );
        }
    }

    @Schema(description = "OTA 예약 정보")
    public record OtaBookingInfo(
        @Schema(description = "OTA 예약 완료 날짜", nullable = true) String bookedAt,
        @Schema(description = "예약 인증 완료 여부") boolean verified
    ) {}
}
