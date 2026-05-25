package roundtrip.user.domain.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import roundtrip.common.entity.BaseEntity;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_follows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFollow extends BaseEntity<UserFollowId> {

    @EmbeddedId
    private UserFollowId id;

    public static UserFollow create(UUID followerId, UUID followingId) {
        Objects.requireNonNull(followerId, "followerId는 null일 수 없습니다");
        Objects.requireNonNull(followingId, "followingId는 null일 수 없습니다");
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("자기 자신을 팔로우할 수 없습니다");
        }
        var follow = new UserFollow();
        follow.id = new UserFollowId(followerId, followingId);
        return follow;
    }

    public UUID followerId() {
        return id.getFollowerId();
    }

    public UUID followingId() {
        return id.getFollowingId();
    }
}
