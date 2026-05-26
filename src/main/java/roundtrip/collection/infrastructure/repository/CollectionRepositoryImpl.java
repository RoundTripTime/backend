package roundtrip.collection.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.collection.domain.entity.Collection;
import roundtrip.collection.domain.entity.CollectionPlace;
import roundtrip.collection.domain.repository.CollectionRepository;
import roundtrip.place.domain.entity.Place;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CollectionRepositoryImpl implements CollectionRepository {

    private final CollectionJpaRepository collectionJpa;
    private final CollectionPlaceJpaRepository collectionPlaceJpa;

    @Override
    public Collection save(Collection collection) {
        return collectionJpa.save(collection);
    }

    @Override
    public Optional<Collection> findById(UUID id) {
        return collectionJpa.findById(id);
    }

    @Override
    public Optional<Collection> findByIdAndUserId(UUID id, UUID userId) {
        return collectionJpa.findByIdAndUserId(id, userId);
    }

    @Override
    public List<Collection> findByUserId(UUID userId) {
        return collectionJpa.findByUserId(userId);
    }

    @Override
    public int countPlacesByCollectionId(UUID collectionId) {
        return collectionPlaceJpa.countByIdCollectionId(collectionId);
    }

    @Override
    public boolean existsPlaceInCollection(UUID collectionId, UUID placeId) {
        return collectionPlaceJpa.existsByIdCollectionIdAndIdPlaceId(collectionId, placeId);
    }

    @Override
    public CollectionPlace addPlace(UUID collectionId, UUID placeId) {
        CollectionPlace cp = CollectionPlace.of(collectionId, placeId);
        return collectionPlaceJpa.save(cp);
    }

    @Override
    @Transactional
    public void removePlace(UUID collectionId, UUID placeId) {
        collectionPlaceJpa.deleteByCollectionIdAndPlaceId(collectionId, placeId);
    }

    @Override
    public List<Place> findPlacesByCollectionId(UUID collectionId) {
        return collectionPlaceJpa.findPlacesByCollectionId(collectionId);
    }

    @Override
    public void deleteById(UUID id) {
        collectionJpa.deleteById(id);
    }

    @Override
    public Optional<Collection> findByShareToken(String shareToken) {
        return collectionJpa.findByShareToken(shareToken);
    }
}
