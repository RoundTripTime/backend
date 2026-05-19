package roundtrip.place.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryImpl implements PlaceRepository {

    private final PlaceJpaRepository jpa;

    @Override
    public Place save(Place place) {
        return jpa.save(place);
    }

    @Override
    public Optional<Place> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Place> findByKakaoPlaceId(String kakaoPlaceId) {
        return jpa.findByKakaoPlaceId(kakaoPlaceId);
    }
}
