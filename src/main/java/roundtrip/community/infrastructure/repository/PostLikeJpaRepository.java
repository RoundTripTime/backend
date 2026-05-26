package roundtrip.community.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.community.domain.entity.PostLike;
import roundtrip.community.domain.entity.PostLikeId;

import java.util.UUID;

interface PostLikeJpaRepository extends JpaRepository<PostLike, PostLikeId> {

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.id.postId = :postId AND pl.id.userId = :userId")
    void deleteByPostIdAndUserId(@Param("postId") UUID postId, @Param("userId") UUID userId);
}
