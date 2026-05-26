package roundtrip.community.domain.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "post_tagged_places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTaggedPlace {

    @EmbeddedId
    private PostTaggedPlaceId id;

    public static PostTaggedPlace of(UUID postId, UUID placeId) {
        var tag = new PostTaggedPlace();
        tag.id = new PostTaggedPlaceId(postId, placeId);
        return tag;
    }

    public UUID getPostId() {
        return id.getPostId();
    }

    public UUID getPlaceId() {
        return id.getPlaceId();
    }
}
