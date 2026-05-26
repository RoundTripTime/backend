package roundtrip.itinerary.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;
import roundtrip.itinerary.domain.repository.ItineraryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ItineraryRepositoryImpl implements ItineraryRepository {

    private final ItineraryJpaRepository itineraryJpa;
    private final ItineraryItemJpaRepository itemJpa;

    @Override
    public Itinerary save(Itinerary itinerary) {
        return itineraryJpa.save(itinerary);
    }

    @Override
    public Optional<Itinerary> findById(UUID id) {
        return itineraryJpa.findById(id);
    }

    @Override
    public Optional<Itinerary> findByIdAndUserId(UUID id, UUID userId) {
        return itineraryJpa.findByIdAndUserId(id, userId);
    }

    @Override
    public List<Itinerary> findByUserId(UUID userId) {
        return itineraryJpa.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public void deleteById(UUID id) {
        itineraryJpa.deleteById(id);
    }

    @Override
    public int countItemsByItineraryId(UUID itineraryId) {
        return itemJpa.countByItineraryId(itineraryId);
    }

    @Override
    public ItineraryItem saveItem(ItineraryItem item) {
        return itemJpa.save(item);
    }

    @Override
    public Optional<ItineraryItem> findItemById(UUID itemId) {
        return itemJpa.findById(itemId);
    }

    @Override
    public Optional<ItineraryItem> findItemByIdAndItineraryId(UUID itemId, UUID itineraryId) {
        return itemJpa.findByIdAndItineraryId(itemId, itineraryId);
    }

    @Override
    public List<ItineraryItem> findItemsByItineraryId(UUID itineraryId) {
        return itemJpa.findByItineraryIdOrderBySortOrderAsc(itineraryId);
    }

    @Override
    public void deleteItemById(UUID itemId) {
        itemJpa.deleteById(itemId);
    }

    @Override
    public List<ItineraryItem> saveAllItems(List<ItineraryItem> items) {
        return itemJpa.saveAll(items);
    }
}
