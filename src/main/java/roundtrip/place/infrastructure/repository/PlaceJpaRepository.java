package roundtrip.place.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.place.domain.entity.Place;

import java.util.Optional;
import java.util.UUID;

interface PlaceJpaRepository extends JpaRepository<Place, UUID> {

    Optional<Place> findByKakaoPlaceId(String kakaoPlaceId);
}
