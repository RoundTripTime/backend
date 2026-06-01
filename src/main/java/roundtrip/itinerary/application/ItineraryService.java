package roundtrip.itinerary.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;
import roundtrip.itinerary.domain.repository.ItineraryRepository;
import roundtrip.itinerary.infrastructure.myrealtrip.MyRealTripClient;
import roundtrip.itinerary.presentation.dto.ReorderItemsRequest;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryService {

    @Value("${app.share-base-url}")
    private String baseShareUrl;

    private final ItineraryRepository itineraryRepository;
    private final PlaceRepository placeRepository;
    private final MyRealTripClient myRealTripClient;

    private static final Map<String, String> REGION_TO_AIRPORT = Map.ofEntries(
            Map.entry("도쿄", "NRT"), Map.entry("오사카", "KIX"), Map.entry("후쿠오카", "FUK"),
            Map.entry("나고야", "NGO"), Map.entry("삿포로", "CTS"), Map.entry("오키나와", "OKA"),
            Map.entry("교토", "KIX"), Map.entry("나라", "KIX"),
            Map.entry("방콕", "BKK"), Map.entry("치앙마이", "CNX"), Map.entry("푸켓", "HKT"),
            Map.entry("하노이", "HAN"), Map.entry("호치민", "SGN"), Map.entry("다낭", "DAD"),
            Map.entry("싱가포르", "SIN"), Map.entry("발리", "DPS"), Map.entry("자카르타", "CGK"),
            Map.entry("마닐라", "MNL"), Map.entry("세부", "CEB"),
            Map.entry("타이베이", "TPE"), Map.entry("홍콩", "HKG"), Map.entry("마카오", "MFM"),
            Map.entry("상하이", "PVG"), Map.entry("베이징", "PEK"),
            Map.entry("괌", "GUM"), Map.entry("사이판", "SPN"), Map.entry("하와이", "HNL"),
            Map.entry("뉴욕", "JFK"), Map.entry("LA", "LAX"), Map.entry("로스앤젤레스", "LAX"),
            Map.entry("파리", "CDG"), Map.entry("런던", "LHR"), Map.entry("로마", "FCO"),
            Map.entry("바르셀로나", "BCN"), Map.entry("시드니", "SYD"),
            Map.entry("제주", "CJU"), Map.entry("부산", "PUS"), Map.entry("서울", "ICN")
    );

    @Transactional(readOnly = true)
    public List<ItinerarySummary> getItineraries(UUID userId) {
        List<Itinerary> itineraries = itineraryRepository.findByUserId(userId);
        return itineraries.stream()
                .map(it -> new ItinerarySummary(it, itineraryRepository.countItemsByItineraryId(it.getId())))
                .toList();
    }

    @Transactional
    public Itinerary createItinerary(UUID userId, String title, String destinationRegion,
                                     LocalDate startDate, LocalDate endDate, int partySize) {
        return itineraryRepository.save(
                Itinerary.create(userId, title, destinationRegion, startDate, endDate, partySize));
    }

    @Transactional(readOnly = true)
    public ItineraryWithItems getItinerary(UUID userId, UUID itineraryId) {
        Itinerary itinerary = findOwnedItinerary(userId, itineraryId);
        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(itineraryId);
        Map<UUID, String> placeNames = resolvePlaceNames(items);
        return new ItineraryWithItems(itinerary, items, placeNames);
    }

    @Transactional
    public Itinerary updateItinerary(UUID userId, UUID itineraryId,
                                     String title, String destinationRegion,
                                     LocalDate startDate, LocalDate endDate,
                                     Integer partySize, String visibility, String status) {
        Itinerary itinerary = findOwnedItinerary(userId, itineraryId);
        itinerary.update(title, destinationRegion, startDate, endDate, partySize, visibility, status);
        return itineraryRepository.save(itinerary);
    }

    @Transactional
    public void deleteItinerary(UUID userId, UUID itineraryId) {
        findOwnedItinerary(userId, itineraryId);
        itineraryRepository.deleteById(itineraryId);
    }

    @Transactional
    public ItineraryItem addItem(UUID userId, UUID itineraryId, UUID placeId,
                                 Integer dayIndex, Integer sortOrder, Integer plannedDurationMinutes,
                                 LocalTime startTime, LocalTime endTime) {
        findOwnedItinerary(userId, itineraryId);
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        ItineraryItem item = ItineraryItem.create(itineraryId, placeId, dayIndex, sortOrder,
                plannedDurationMinutes, startTime, endTime);
        return itineraryRepository.saveItem(item);
    }

    @Transactional
    public ItineraryItem updateItem(UUID userId, UUID itineraryId, UUID itemId,
                                    Integer dayIndex, Integer sortOrder, Integer plannedDurationMinutes,
                                    LocalTime startTime, LocalTime endTime) {
        findOwnedItinerary(userId, itineraryId);
        ItineraryItem item = itineraryRepository.findItemByIdAndItineraryId(itemId, itineraryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND));
        item.update(dayIndex, sortOrder, plannedDurationMinutes, startTime, endTime);
        return itineraryRepository.saveItem(item);
    }

    @Transactional
    public void removeItem(UUID userId, UUID itineraryId, UUID itemId) {
        findOwnedItinerary(userId, itineraryId);
        itineraryRepository.findItemByIdAndItineraryId(itemId, itineraryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND));
        itineraryRepository.deleteItemById(itemId);
    }

    @Transactional
    public void reorderItems(UUID userId, UUID itineraryId, List<ReorderItemsRequest.ReorderEntry> entries) {
        findOwnedItinerary(userId, itineraryId);
        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(itineraryId);
        Map<UUID, ItineraryItem> itemMap = items.stream()
                .collect(Collectors.toMap(ItineraryItem::getId, i -> i));

        for (ReorderItemsRequest.ReorderEntry entry : entries) {
            ItineraryItem item = itemMap.get(entry.itemId());
            if (item == null) {
                throw new BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND);
            }
            item.reorder(entry.dayIndex(), entry.sortOrder());
        }
        itineraryRepository.saveAllItems(items);
    }

    @Transactional
    public ShareInfo getShareLink(UUID userId, UUID itineraryId) {
        Itinerary itinerary = findOwnedItinerary(userId, itineraryId);
        String token = itinerary.ensureShareToken();
        itineraryRepository.save(itinerary);
        String shareUrl = baseShareUrl.replace("/collections/", "/itineraries/") + token;
        return new ShareInfo(shareUrl, itinerary.getVisibility());
    }

    @Transactional(readOnly = true)
    public OtaLinkResult generateOtaLink(UUID userId, UUID itineraryId, String type) {
        Itinerary itinerary = findOwnedItinerary(userId, itineraryId);
        String dest = itinerary.getDestinationRegion();
        String checkin = itinerary.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String checkout = itinerary.getEndDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int adults = itinerary.getPartySize();

        return switch (type) {
            case "accommodation" -> generateAccommodationLink(dest, checkin, checkout, adults);
            case "flight" -> generateFlightLink(dest, checkin, checkout, adults);
            case "activity" -> generateActivityLink(dest);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 OTA 타입입니다: " + type);
        };
    }

    private OtaLinkResult generateAccommodationLink(String dest, String checkIn, String checkOut, int adults) {
        String city = extractCityName(dest);
        try {
            var result = myRealTripClient.searchAccommodation(city, checkIn, checkOut, adults);
            if (result.items() != null && !result.items().isEmpty()) {
                var top = result.items().get(0);
                String targetUrl = "https://www.myrealtrip.com/offers/" + top.itemId();
                var mylink = myRealTripClient.createMyLink(targetUrl);
                return new OtaLinkResult(mylink.mylink());
            }
        } catch (Exception e) {
            log.warn("마이리얼트립 숙소 검색/링크 생성 실패: {}", e.getMessage());
        }
        // 폴백: 마이리얼트립 숙소 검색 페이지로 직접 링크
        return new OtaLinkResult("https://www.myrealtrip.com/accommodations?keyword=" + city);
    }

    private OtaLinkResult generateFlightLink(String dest, String depDate, String arrDate, int adults) {
        String city = extractCityName(dest);
        String arrAirport = REGION_TO_AIRPORT.get(city);
        if (arrAirport != null) {
            try {
                String landingUrl = myRealTripClient.createFlightLandingUrl(
                        "ICN", arrAirport, "RT", depDate, arrDate, adults);
                var mylink = myRealTripClient.createMyLink(landingUrl);
                return new OtaLinkResult(mylink.mylink());
            } catch (Exception e) {
                log.warn("마이리얼트립 항공 랜딩URL/마이링크 생성 실패: {}", e.getMessage());
            }
        }
        // 폴백: 마이리얼트립 항공 페이지로 직접 링크
        return new OtaLinkResult("https://www.myrealtrip.com/flights");
    }

    private OtaLinkResult generateActivityLink(String dest) {
        String city = extractCityName(dest);
        try {
            var result = myRealTripClient.searchTna(city, city);
            if (result.items() != null && !result.items().isEmpty()) {
                var top = result.items().get(0);
                String targetUrl = top.productUrl();
                if (targetUrl == null || targetUrl.isBlank()) {
                    targetUrl = "https://www.myrealtrip.com/offers/" + top.gid();
                }
                var mylink = myRealTripClient.createMyLink(targetUrl);
                return new OtaLinkResult(mylink.mylink());
            }
        } catch (Exception e) {
            log.warn("마이리얼트립 투어/티켓 검색/링크 생성 실패: {}", e.getMessage());
        }
        // 폴백
        return new OtaLinkResult("https://www.myrealtrip.com/q/" + city);
    }

    private String extractCityName(String destinationRegion) {
        // "도쿄, 일본" → "도쿄", "오사카" → "오사카"
        if (destinationRegion.contains(",")) {
            return destinationRegion.split(",")[0].trim();
        }
        return destinationRegion.trim();
    }

    private Itinerary findOwnedItinerary(UUID userId, UUID itineraryId) {
        return itineraryRepository.findByIdAndUserId(itineraryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
    }

    private Map<UUID, String> resolvePlaceNames(List<ItineraryItem> items) {
        List<UUID> placeIds = items.stream().map(ItineraryItem::getPlaceId).distinct().toList();
        return placeIds.stream()
                .map(id -> placeRepository.findById(id).orElse(null))
                .filter(p -> p != null)
                .collect(Collectors.toMap(Place::getId, Place::getCanonicalName));
    }

    public record ItinerarySummary(Itinerary itinerary, int placeCount) {}

    public record ItineraryWithItems(Itinerary itinerary, List<ItineraryItem> items, Map<UUID, String> placeNames) {}

    public record ShareInfo(String shareUrl, String visibility) {}

    public record OtaLinkResult(String otaUrl) {}
}
