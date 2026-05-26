package roundtrip.itinerary.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;
import roundtrip.itinerary.domain.repository.ItineraryRepository;
import roundtrip.itinerary.presentation.dto.ReorderItemsRequest;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItineraryService {

    @Value("${app.share-base-url}")
    private String baseShareUrl;

    private final ItineraryRepository itineraryRepository;
    private final PlaceRepository placeRepository;

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
                                 Integer dayIndex, Integer sortOrder, Integer plannedDurationMinutes) {
        findOwnedItinerary(userId, itineraryId);
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        ItineraryItem item = ItineraryItem.create(itineraryId, placeId, dayIndex, sortOrder, plannedDurationMinutes);
        return itineraryRepository.saveItem(item);
    }

    @Transactional
    public ItineraryItem updateItem(UUID userId, UUID itineraryId, UUID itemId,
                                    Integer dayIndex, Integer sortOrder, Integer plannedDurationMinutes) {
        findOwnedItinerary(userId, itineraryId);
        ItineraryItem item = itineraryRepository.findItemByIdAndItineraryId(itemId, itineraryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND));
        item.update(dayIndex, sortOrder, plannedDurationMinutes);
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
    public String generateOtaLink(UUID userId, UUID itineraryId, String type) {
        Itinerary itinerary = findOwnedItinerary(userId, itineraryId);
        String dest = itinerary.getDestinationRegion();
        String checkin = itinerary.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String checkout = itinerary.getEndDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int adults = itinerary.getPartySize();

        return switch (type) {
            case "accommodation" ->
                    "https://partner.ota.com/search?type=accommodation&checkin=" + checkin
                            + "&checkout=" + checkout + "&dest=" + dest + "&adults=" + adults;
            case "flight" ->
                    "https://partner.ota.com/search?type=flight&departure=" + checkin
                            + "&return=" + checkout + "&dest=" + dest + "&passengers=" + adults;
            case "activity" ->
                    "https://partner.ota.com/search?type=activity&date=" + checkin
                            + "&dest=" + dest + "&participants=" + adults;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 OTA 타입입니다: " + type);
        };
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
}
