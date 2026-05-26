package roundtrip.community.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.community.domain.entity.CommunityPost;

import java.util.List;
import java.util.UUID;

interface CommunityPostJpaRepository extends JpaRepository<CommunityPost, UUID> {

    @Query("""
        SELECT p FROM CommunityPost p
        WHERE p.visibility = 'public'
        AND (:cursorId IS NULL OR p.createdAt < (SELECT cp.createdAt FROM CommunityPost cp WHERE cp.id = :cursorId))
        ORDER BY p.createdAt DESC
        """)
    List<CommunityPost> findPublicPostsBefore(@Param("cursorId") UUID cursorId,
                                               @Param("limit") int limit);

    @Query("""
        SELECT p FROM CommunityPost p
        WHERE p.userId IN (
            SELECT uf.id.followingId FROM UserFollow uf WHERE uf.id.followerId = :userId
        )
        AND (:cursorId IS NULL OR p.createdAt < (SELECT cp.createdAt FROM CommunityPost cp WHERE cp.id = :cursorId))
        ORDER BY p.createdAt DESC
        """)
    List<CommunityPost> findFollowingPostsBefore(@Param("userId") UUID userId,
                                                  @Param("cursorId") UUID cursorId,
                                                  @Param("limit") int limit);

    int countByUserId(UUID userId);
}
