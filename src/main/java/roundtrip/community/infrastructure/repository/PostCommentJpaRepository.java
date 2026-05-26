package roundtrip.community.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.community.domain.entity.PostComment;

import java.util.List;
import java.util.UUID;

interface PostCommentJpaRepository extends JpaRepository<PostComment, UUID> {

    @Query("""
        SELECT c FROM PostComment c
        WHERE c.postId = :postId
        AND (:cursorId IS NULL OR c.createdAt < (SELECT cc.createdAt FROM PostComment cc WHERE cc.id = :cursorId))
        ORDER BY c.createdAt DESC
        """)
    List<PostComment> findByPostIdBefore(@Param("postId") UUID postId,
                                          @Param("cursorId") UUID cursorId,
                                          @Param("limit") int limit);
}
