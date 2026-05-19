package roundtrip.collection.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.collection.domain.entity.Collection;
import roundtrip.collection.domain.repository.CollectionRepository;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionService {

    private static final String BASE_SHARE_URL = "https://app.example.com/share/collections/";

    private final CollectionRepository collectionRepository;
    private final PlaceRepository placeRepository;

    @Transactional
    public void createDefaultCollection(UUID userId) {
        Collection defaultCollection = Collection.createDefault(userId);
        collectionRepository.save(defaultCollection);
    }

    @Transactional(readOnly = true)
    public List<CollectionSummary> getCollections(UUID userId) {
        List<Collection> collections = collectionRepository.findByUserId(userId);
        return collections.stream()
                .map(c -> new CollectionSummary(c, collectionRepository.countPlacesByCollectionId(c.getId())))
                .toList();
    }

    @Transactional
    public Collection createCollection(UUID userId, String name, String icon) {
        Collection collection = Collection.create(userId, name, icon);
        return collectionRepository.save(collection);
    }

    @Transactional
    public Collection updateCollection(UUID userId, UUID collectionId, String name, String icon, String visibility) {
        Collection collection = findOwnedCollection(userId, collectionId);
        collection.update(name, icon, visibility);
        return collectionRepository.save(collection);
    }

    @Transactional
    public void deleteCollection(UUID userId, UUID collectionId) {
        Collection collection = findOwnedCollection(userId, collectionId);
        if (collection.isDefault()) {
            throw new BusinessException(ErrorCode.COLLECTION_DEFAULT_PROTECTED);
        }
        collectionRepository.deleteById(collectionId);
    }

    @Transactional(readOnly = true)
    public CollectionWithPlaces getCollectionPlaces(UUID userId, UUID collectionId) {
        Collection collection = findOwnedCollection(userId, collectionId);
        List<Place> places = collectionRepository.findPlacesByCollectionId(collectionId);
        return new CollectionWithPlaces(collection, places);
    }

    @Transactional
    public void addPlace(UUID userId, UUID collectionId, UUID placeId) {
        findOwnedCollection(userId, collectionId);
        placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        if (collectionRepository.existsPlaceInCollection(collectionId, placeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 플레이스에 추가된 장소입니다.");
        }
        collectionRepository.addPlace(collectionId, placeId);
    }

    @Transactional
    public void removePlace(UUID userId, UUID collectionId, UUID placeId) {
        findOwnedCollection(userId, collectionId);
        collectionRepository.removePlace(collectionId, placeId);
    }

    @Transactional
    public ShareInfo getShareLink(UUID userId, UUID collectionId) {
        Collection collection = findOwnedCollection(userId, collectionId);
        String token = collection.ensureShareToken();
        collectionRepository.save(collection);
        return new ShareInfo(BASE_SHARE_URL + token, collection.getVisibility());
    }

    private Collection findOwnedCollection(UUID userId, UUID collectionId) {
        return collectionRepository.findByIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COLLECTION_NOT_FOUND));
    }

    public record CollectionSummary(Collection collection, int placeCount) {}

    public record CollectionWithPlaces(Collection collection, List<Place> places) {}

    public record ShareInfo(String shareUrl, String visibility) {}
}
