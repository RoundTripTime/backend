package roundtrip.market.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;
import roundtrip.itinerary.domain.repository.ItineraryRepository;
import roundtrip.market.domain.entity.CreditHistory;
import roundtrip.market.domain.entity.MarketPlan;
import roundtrip.market.domain.repository.MarketRepository;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.repository.UserRepository;

import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;
    private final ItineraryRepository itineraryRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;

    // ── 목록 조회 ──

    @Transactional(readOnly = true)
    public MarketListResult getMarketPlans(String keyword, String sort, UUID cursorId, int limit) {
        List<MarketPlan> plans = marketRepository.findListedPlans(keyword, sort, cursorId, limit + 1);

        boolean hasMore = plans.size() > limit;
        if (hasMore) {
            plans = plans.subList(0, limit);
        }
        UUID nextCursor = hasMore && !plans.isEmpty() ? plans.getLast().getId() : null;

        List<MarketPlanCard> cards = plans.stream()
            .map(this::toCard)
            .toList();

        return new MarketListResult(cards, nextCursor);
    }

    // ── 등록 ──

    @Transactional
    public MarketPlan createMarketPlan(UUID userId, UUID itineraryId, String title,
                                        String description, String highlight,
                                        String pros, String cons, String tips) {
        itineraryRepository.findByIdAndUserId(itineraryId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));

        if (marketRepository.existsByItineraryId(itineraryId)) {
            throw new BusinessException(ErrorCode.ALREADY_LISTED);
        }

        MarketPlan plan = MarketPlan.create(itineraryId, userId, title, description,
            highlight, pros, cons, tips);
        return marketRepository.savePlan(plan);
    }

    // ── 미리보기 ──

    @Transactional(readOnly = true)
    public PreviewResult getPreview(UUID marketPlanId, UUID userId) {
        MarketPlan plan = findListedPlan(marketPlanId);
        Itinerary itinerary = itineraryRepository.findById(plan.getItineraryId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
        User author = userRepository.findById(plan.getUserId()).orElse(null);

        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(plan.getItineraryId());
        Place previewPlace = null;
        if (!items.isEmpty()) {
            previewPlace = placeRepository.findById(items.getFirst().getPlaceId()).orElse(null);
        }

        int totalPlaces = items.size();
        int hiddenCount = previewPlace != null ? totalPlaces - 1 : totalPlaces;
        boolean isPurchased = plan.getUserId().equals(userId)
            || marketRepository.hasPurchased(userId, marketPlanId);

        return new PreviewResult(plan, itinerary, author, previewPlace, hiddenCount, isPurchased);
    }

    // ── 상세 조회 (크레딧 차감) ──

    @Transactional
    public DetailResult getDetail(UUID marketPlanId, UUID userId) {
        MarketPlan plan = findListedPlan(marketPlanId);
        Itinerary itinerary = itineraryRepository.findById(plan.getItineraryId())
            .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
        User author = userRepository.findById(plan.getUserId()).orElse(null);

        boolean isOwner = plan.getUserId().equals(userId);
        boolean alreadyPurchased = marketRepository.hasPurchased(userId, marketPlanId);

        // 크레딧 차감 (본인 플랜이거나 이미 구매한 경우 차감 안함)
        if (!isOwner && !alreadyPurchased) {
            User buyer = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (buyer.getCreditBalance() < plan.getCreditPrice()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CREDITS);
            }

            // 크레딧 차감 기록
            int newBalance = buyer.getCreditBalance() - plan.getCreditPrice();
            CreditHistory history = CreditHistory.create(userId, "plan_purchase",
                -plan.getCreditPrice(), newBalance,
                "플랜 마켓 열람: " + plan.getTitle() + " [" + marketPlanId + "]");
            marketRepository.saveCredit(history);

            // 판매자에게 크레딧 적립
            User seller = userRepository.findById(plan.getUserId()).orElse(null);
            if (seller != null) {
                int sellerBalance = seller.getCreditBalance() + plan.getCreditPrice();
                CreditHistory saleHistory = CreditHistory.create(plan.getUserId(), "plan_sale",
                    plan.getCreditPrice(), sellerBalance,
                    "플랜 마켓 판매: " + plan.getTitle() + " [" + marketPlanId + "]");
                marketRepository.saveCredit(saleHistory);
            }

            // view_count 증가
            plan.incrementViewCount();
            marketRepository.savePlan(plan);
        }

        // 일정 아이템을 day별로 그룹핑
        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(plan.getItineraryId());
        Map<Integer, List<DayItem>> dayMap = new TreeMap<>();
        for (ItineraryItem item : items) {
            int dayIndex = item.getDayIndex() != null ? item.getDayIndex() : 0;
            Place place = placeRepository.findById(item.getPlaceId()).orElse(null);
            if (place != null) {
                dayMap.computeIfAbsent(dayIndex, k -> new ArrayList<>())
                    .add(new DayItem(place, item.getPlannedDurationMinutes(), item.getSortOrder()));
            }
        }

        List<DayGroup> days = dayMap.entrySet().stream()
            .map(e -> new DayGroup(e.getKey(), e.getValue()))
            .toList();

        return new DetailResult(plan, itinerary, author, days);
    }

    // ── 등록 취소 ──

    @Transactional
    public void unlistPlan(UUID marketPlanId, UUID userId) {
        MarketPlan plan = findListedPlan(marketPlanId);
        if (!plan.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "타인의 플랜을 삭제할 수 없습니다.");
        }
        plan.unlist();
        marketRepository.savePlan(plan);
    }

    // ── helpers ──

    private MarketPlan findListedPlan(UUID marketPlanId) {
        MarketPlan plan = marketRepository.findPlanById(marketPlanId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_PLAN_NOT_FOUND));
        if (!plan.isListed()) {
            throw new BusinessException(ErrorCode.MARKET_PLAN_NOT_FOUND);
        }
        return plan;
    }

    private MarketPlanCard toCard(MarketPlan plan) {
        Itinerary itinerary = itineraryRepository.findById(plan.getItineraryId()).orElse(null);
        User author = userRepository.findById(plan.getUserId()).orElse(null);
        int placeCount = itineraryRepository.countItemsByItineraryId(plan.getItineraryId());

        // cover thumbnail: 첫 번째 장소의 썸네일
        String coverThumbnail = null;
        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(plan.getItineraryId());
        if (!items.isEmpty()) {
            Place firstPlace = placeRepository.findById(items.getFirst().getPlaceId()).orElse(null);
            if (firstPlace != null) {
                coverThumbnail = firstPlace.getThumbnailUrl();
            }
        }

        int durationNights = 0;
        if (itinerary != null && itinerary.getStartDate() != null && itinerary.getEndDate() != null) {
            durationNights = (int) ChronoUnit.DAYS.between(itinerary.getStartDate(), itinerary.getEndDate());
        }

        return new MarketPlanCard(plan, itinerary, author, coverThumbnail, placeCount, durationNights);
    }

    // ── result records ──

    public record MarketPlanCard(
        MarketPlan plan, Itinerary itinerary, User author,
        String coverThumbnailUrl, int placeCount, int durationNights
    ) {}

    public record MarketListResult(List<MarketPlanCard> items, UUID nextCursor) {}

    public record PreviewResult(
        MarketPlan plan, Itinerary itinerary, User author,
        Place previewPlace, int hiddenPlaceCount, boolean isPurchased
    ) {}

    public record DayItem(Place place, Integer plannedDurationMinutes, Integer sortOrder) {}

    public record DayGroup(int dayIndex, List<DayItem> items) {}

    public record DetailResult(
        MarketPlan plan, Itinerary itinerary, User author, List<DayGroup> days
    ) {}
}
