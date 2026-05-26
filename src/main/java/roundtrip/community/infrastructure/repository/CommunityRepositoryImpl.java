package roundtrip.community.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.community.domain.entity.*;
import roundtrip.community.domain.repository.CommunityRepository;
import roundtrip.place.domain.entity.Place;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CommunityRepositoryImpl implements CommunityRepository {

    private final CommunityPostJpaRepository postJpa;
    private final PostTaggedPlaceJpaRepository taggedPlaceJpa;
    private final PostTaggedItineraryJpaRepository taggedItineraryJpa;
    private final PostLikeJpaRepository likeJpa;
    private final PostCommentJpaRepository commentJpa;
    private final UserFollowJpaRepository followJpa;
    private final EntityManager em;

    // ── Posts ──

    @Override
    public CommunityPost savePost(CommunityPost post) {
        return postJpa.save(post);
    }

    @Override
    public Optional<CommunityPost> findPostById(UUID postId) {
        return postJpa.findById(postId);
    }

    @Override
    public void deletePost(CommunityPost post) {
        postJpa.delete(post);
    }

    @Override
    public List<CommunityPost> findPublicPostsBefore(UUID cursorId, int limit) {
        String jpql = "SELECT p FROM CommunityPost p WHERE p.visibility = 'public'";
        if (cursorId != null) {
            jpql += " AND p.createdAt < (SELECT cp.createdAt FROM CommunityPost cp WHERE cp.id = :cursorId)";
        }
        jpql += " ORDER BY p.createdAt DESC";

        TypedQuery<CommunityPost> query = em.createQuery(jpql, CommunityPost.class);
        if (cursorId != null) {
            query.setParameter("cursorId", cursorId);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public List<CommunityPost> findFollowingPostsBefore(UUID userId, UUID cursorId, int limit) {
        String jpql = """
            SELECT p FROM CommunityPost p
            WHERE p.userId IN (
                SELECT uf.id.followingId FROM UserFollow uf WHERE uf.id.followerId = :userId
            )
            """;
        if (cursorId != null) {
            jpql += " AND p.createdAt < (SELECT cp.createdAt FROM CommunityPost cp WHERE cp.id = :cursorId)";
        }
        jpql += " ORDER BY p.createdAt DESC";

        TypedQuery<CommunityPost> query = em.createQuery(jpql, CommunityPost.class);
        query.setParameter("userId", userId);
        if (cursorId != null) {
            query.setParameter("cursorId", cursorId);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    // ── Tagged Places ──

    @Override
    public void saveTaggedPlace(PostTaggedPlace tag) {
        taggedPlaceJpa.save(tag);
    }

    @Override
    public List<Place> findTaggedPlacesByPostId(UUID postId) {
        return taggedPlaceJpa.findPlacesByPostId(postId);
    }

    // ── Tagged Itineraries ──

    @Override
    public void saveTaggedItinerary(PostTaggedItinerary tag) {
        taggedItineraryJpa.save(tag);
    }

    @Override
    public Optional<TaggedItineraryInfo> findTaggedItineraryByPostId(UUID postId) {
        var tags = taggedItineraryJpa.findByIdPostId(postId);
        if (tags.isEmpty()) {
            return Optional.empty();
        }
        UUID itineraryId = tags.getFirst().getItineraryId();
        var result = em.createQuery(
                "SELECT i.title FROM roundtrip.itinerary.domain.entity.Itinerary i WHERE i.id = :id", String.class)
            .setParameter("id", itineraryId)
            .getResultList();
        if (result.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TaggedItineraryInfo(itineraryId, result.getFirst()));
    }

    // ── Likes ──

    @Override
    public PostLike saveLike(PostLike like) {
        return likeJpa.save(like);
    }

    @Override
    public boolean existsLike(UUID postId, UUID userId) {
        return likeJpa.existsByIdPostIdAndIdUserId(postId, userId);
    }

    @Override
    @Transactional
    public void deleteLike(UUID postId, UUID userId) {
        likeJpa.deleteByPostIdAndUserId(postId, userId);
    }

    // ── Comments ──

    @Override
    public PostComment saveComment(PostComment comment) {
        return commentJpa.save(comment);
    }

    @Override
    public Optional<PostComment> findCommentById(UUID commentId) {
        return commentJpa.findById(commentId);
    }

    @Override
    public void deleteComment(PostComment comment) {
        commentJpa.delete(comment);
    }

    @Override
    public List<PostComment> findCommentsByPostIdBefore(UUID postId, UUID cursorId, int limit) {
        String jpql = "SELECT c FROM PostComment c WHERE c.postId = :postId";
        if (cursorId != null) {
            jpql += " AND c.createdAt < (SELECT cc.createdAt FROM PostComment cc WHERE cc.id = :cursorId)";
        }
        jpql += " ORDER BY c.createdAt DESC";

        TypedQuery<PostComment> query = em.createQuery(jpql, PostComment.class);
        query.setParameter("postId", postId);
        if (cursorId != null) {
            query.setParameter("cursorId", cursorId);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    // ── Follows ──

    @Override
    public int countFollowers(UUID userId) {
        return followJpa.countByIdFollowingId(userId);
    }

    @Override
    public int countFollowing(UUID userId) {
        return followJpa.countByIdFollowerId(userId);
    }

    @Override
    public boolean isFollowing(UUID followerId, UUID followingId) {
        return followJpa.existsByIdFollowerIdAndIdFollowingId(followerId, followingId);
    }

    @Override
    public void saveFollow(roundtrip.user.domain.entity.UserFollow follow) {
        followJpa.save(follow);
    }

    @Override
    @Transactional
    public void deleteFollow(UUID followerId, UUID followingId) {
        followJpa.deleteByFollowerAndFollowing(followerId, followingId);
    }

    // ── Posts count ──

    @Override
    public int countPostsByUserId(UUID userId) {
        return postJpa.countByUserId(userId);
    }
}
