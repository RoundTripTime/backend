package roundtrip.community.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "post_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static PostComment create(UUID postId, UUID userId, String body) {
        Objects.requireNonNull(postId, "postId는 null일 수 없습니다");
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다");
        Objects.requireNonNull(body, "body는 null일 수 없습니다");

        var comment = new PostComment();
        comment.postId = postId;
        comment.userId = userId;
        comment.body = body;
        comment.createdAt = OffsetDateTime.now();
        return comment;
    }
}
