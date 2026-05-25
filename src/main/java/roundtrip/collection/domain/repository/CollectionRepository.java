package roundtrip.collection.domain.repository;

import roundtrip.collection.domain.entity.Collection;
import roundtrip.collection.domain.entity.CollectionPlace;
import roundtrip.place.domain.entity.Place;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository {

    Collection save(Collection collection);

    Optional<Collection> findById(UUID id);

    Optional<Collection> findByIdAndUserId(UUID id, UUID userId);

    List<Collection> findByUserId(UUID userId);

    int countPlacesByCollectionId(UUID collectionId);

    boolean existsPlaceInCollection(UUID collectionId, UUID placeId);

    CollectionPlace addPlace(UUID collectionId, UUID placeId);

    void removePlace(UUID collectionId, UUID placeId);

    List<Place> findPlacesByCollectionId(UUID collectionId);

    void deleteById(UUID id);
}
