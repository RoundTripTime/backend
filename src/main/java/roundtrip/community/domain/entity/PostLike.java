package roundtrip.community.domain.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;

import java.util.UUID;

@Entity
@Table(name = "post_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike extends BaseEntity<PostLikeId> {

    @EmbeddedId
    private PostLikeId id;

    public static PostLike of(UUID postId, UUID userId) {
        var like = new PostLike();
        like.id = new PostLikeId(postId, userId);
        return like;
    }

    public UUID getPostId() {
        return id.getPostId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }
}
