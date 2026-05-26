package roundtrip.community.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import roundtrip.user.domain.entity.UserFollow;
import roundtrip.user.domain.entity.UserFollowId;

import java.util.UUID;

interface UserFollowJpaRepository extends JpaRepository<UserFollow, UserFollowId> {

    int countByIdFollowingId(UUID followingId);

    int countByIdFollowerId(UUID followerId);

    boolean existsByIdFollowerIdAndIdFollowingId(UUID followerId, UUID followingId);

    @Modifying
    @Query("DELETE FROM UserFollow uf WHERE uf.id.followerId = :followerId AND uf.id.followingId = :followingId")
    void deleteByFollowerAndFollowing(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);
}
