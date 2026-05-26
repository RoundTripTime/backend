package roundtrip.community.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class PostLikeId implements Serializable {

    @Column(name = "post_id", columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;
}
