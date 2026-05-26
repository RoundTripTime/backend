package roundtrip.community.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.community.domain.entity.PostTaggedPlace;
import roundtrip.community.domain.entity.PostTaggedPlaceId;
import roundtrip.place.domain.entity.Place;

import java.util.List;
import java.util.UUID;

interface PostTaggedPlaceJpaRepository extends JpaRepository<PostTaggedPlace, PostTaggedPlaceId> {

    @Query("SELECT p FROM Place p WHERE p.id IN " +
           "(SELECT tp.id.placeId FROM PostTaggedPlace tp WHERE tp.id.postId = :postId)")
    List<Place> findPlacesByPostId(@Param("postId") UUID postId);
}
