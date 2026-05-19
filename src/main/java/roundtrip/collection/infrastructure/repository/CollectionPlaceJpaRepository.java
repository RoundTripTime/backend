package roundtrip.collection.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.collection.domain.entity.CollectionPlace;
import roundtrip.collection.domain.entity.CollectionPlaceId;
import roundtrip.place.domain.entity.Place;

import java.util.List;
import java.util.UUID;

interface CollectionPlaceJpaRepository extends JpaRepository<CollectionPlace, CollectionPlaceId> {

    int countByIdCollectionId(UUID collectionId);

    boolean existsByIdCollectionIdAndIdPlaceId(UUID collectionId, UUID placeId);

    @Query("SELECT p FROM Place p WHERE p.id IN " +
           "(SELECT cp.id.placeId FROM CollectionPlace cp WHERE cp.id.collectionId = :collectionId) " +
           "ORDER BY p.canonicalName")
    List<Place> findPlacesByCollectionId(@Param("collectionId") UUID collectionId);

    @Modifying
    @Query("DELETE FROM CollectionPlace cp WHERE cp.id.collectionId = :collectionId AND cp.id.placeId = :placeId")
    void deleteByCollectionIdAndPlaceId(@Param("collectionId") UUID collectionId, @Param("placeId") UUID placeId);
}
