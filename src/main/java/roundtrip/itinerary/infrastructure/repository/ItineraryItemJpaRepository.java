package roundtrip.itinerary.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.itinerary.domain.entity.ItineraryItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ItineraryItemJpaRepository extends JpaRepository<ItineraryItem, UUID> {

    List<ItineraryItem> findByItineraryIdOrderBySortOrderAsc(UUID itineraryId);

    Optional<ItineraryItem> findByIdAndItineraryId(UUID id, UUID itineraryId);

    int countByItineraryId(UUID itineraryId);
}
