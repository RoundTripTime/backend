package roundtrip.itinerary.domain.repository;

import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItineraryRepository {

    Itinerary save(Itinerary itinerary);

    Optional<Itinerary> findById(UUID id);

    Optional<Itinerary> findByIdAndUserId(UUID id, UUID userId);

    List<Itinerary> findByUserId(UUID userId);

    void deleteById(UUID id);

    int countItemsByItineraryId(UUID itineraryId);

    ItineraryItem saveItem(ItineraryItem item);

    Optional<ItineraryItem> findItemById(UUID itemId);

    Optional<ItineraryItem> findItemByIdAndItineraryId(UUID itemId, UUID itineraryId);

    List<ItineraryItem> findItemsByItineraryId(UUID itineraryId);

    void deleteItemById(UUID itemId);

    List<ItineraryItem> saveAllItems(List<ItineraryItem> items);
}
