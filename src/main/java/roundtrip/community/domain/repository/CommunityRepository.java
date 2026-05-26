package roundtrip.community.domain.repository;

import roundtrip.community.domain.entity.*;
import roundtrip.place.domain.entity.Place;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityRepository {

    // ── Posts ──
    CommunityPost savePost(CommunityPost post);

    Optional<CommunityPost> findPostById(UUID postId);

    void deletePost(CommunityPost post);

    List<CommunityPost> findPublicPostsBefore(UUID cursorId, int limit);

    List<CommunityPost> findFollowingPostsBefore(UUID userId, UUID cursorId, int limit);

    // ── Tagged Places ──
    void saveTaggedPlace(PostTaggedPlace tag);

    List<Place> findTaggedPlacesByPostId(UUID postId);

    // ── Tagged Itineraries ──
    void saveTaggedItinerary(PostTaggedItinerary tag);

    record TaggedItineraryInfo(UUID itineraryId, String title) {}

    Optional<TaggedItineraryInfo> findTaggedItineraryByPostId(UUID postId);

    // ── Likes ──
    PostLike saveLike(PostLike like);

    boolean existsLike(UUID postId, UUID userId);

    void deleteLike(UUID postId, UUID userId);

    // ── Comments ──
    PostComment saveComment(PostComment comment);

    Optional<PostComment> findCommentById(UUID commentId);

    void deleteComment(PostComment comment);

    List<PostComment> findCommentsByPostIdBefore(UUID postId, UUID cursorId, int limit);

    // ── Follows ──
    int countFollowers(UUID userId);

    int countFollowing(UUID userId);

    boolean isFollowing(UUID followerId, UUID followingId);

    void saveFollow(roundtrip.user.domain.entity.UserFollow follow);

    void deleteFollow(UUID followerId, UUID followingId);

    // ── Posts count ──
    int countPostsByUserId(UUID userId);
}
