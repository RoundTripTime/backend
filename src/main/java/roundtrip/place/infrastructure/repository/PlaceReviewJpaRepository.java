package roundtrip.place.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.place.domain.entity.PlaceReview;

import java.util.Optional;
import java.util.UUID;

interface PlaceReviewJpaRepository extends JpaRepository<PlaceReview, UUID> {

    boolean existsByPlaceIdAndUserId(UUID placeId, UUID userId);

    Optional<PlaceReview> findByIdAndPlaceId(UUID id, UUID placeId);
}
