package roundtrip.place.domain.repository;

import roundtrip.place.domain.entity.Place;

import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository {

    Place save(Place place);

    Optional<Place> findById(UUID id);

    Optional<Place> findByKakaoPlaceId(String kakaoPlaceId);
}
