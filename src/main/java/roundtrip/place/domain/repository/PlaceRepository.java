package roundtrip.place.domain.repository;

import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.entity.PlaceCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository {

    Place save(Place place);

    Optional<Place> findById(UUID id);

    Optional<Place> findByKakaoPlaceId(String kakaoPlaceId);

    List<Place> findSimilarPlaces(UUID placeId, int limit);

    List<PlaceSimilarRow> findSimilarPlacesRanked(UUID placeId, int limit);

    List<DiscoverRow> findDiscoverPlaces(UUID userId, int limit, PlaceCategory category, String countryCode);

    List<DiscoverRow> findDiscoverPlacesColdStart(UUID userId, int limit, PlaceCategory category, String countryCode, List<UUID> excludeIds);

    record PlaceSimilarRow(
            UUID id, String canonicalName, PlaceCategory category,
            java.math.BigDecimal latitude, java.math.BigDecimal longitude,
            String thumbnailUrl, String thumbnailSource, double similarityScore) {}

    record DiscoverRow(
            UUID id, String canonicalName, PlaceCategory category,
            java.math.BigDecimal latitude, java.math.BigDecimal longitude,
            String countryCode, String thumbnailUrl, String thumbnailSource, double similarityScore) {}
}
