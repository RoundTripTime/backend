package roundtrip.community.domain.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "post_tagged_itineraries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTaggedItinerary {

    @EmbeddedId
    private PostTaggedItineraryId id;

    public static PostTaggedItinerary of(UUID postId, UUID itineraryId) {
        var tag = new PostTaggedItinerary();
        tag.id = new PostTaggedItineraryId(postId, itineraryId);
        return tag;
    }

    public UUID getPostId() {
        return id.getPostId();
    }

    public UUID getItineraryId() {
        return id.getItineraryId();
    }
}
