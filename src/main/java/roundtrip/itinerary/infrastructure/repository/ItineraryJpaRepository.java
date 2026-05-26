package roundtrip.itinerary.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.itinerary.domain.entity.Itinerary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ItineraryJpaRepository extends JpaRepository<Itinerary, UUID> {

    List<Itinerary> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Itinerary> findByIdAndUserId(UUID id, UUID userId);
}
