package roundtrip.community.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import roundtrip.community.domain.entity.PostTaggedItinerary;
import roundtrip.community.domain.entity.PostTaggedItineraryId;

import java.util.List;
import java.util.UUID;

interface PostTaggedItineraryJpaRepository extends JpaRepository<PostTaggedItinerary, PostTaggedItineraryId> {

    List<PostTaggedItinerary> findByIdPostId(UUID postId);
}
