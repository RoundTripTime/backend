package roundtrip.community.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.community.domain.entity.*;
import roundtrip.community.domain.repository.CommunityRepository;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.place.domain.entity.Place;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.entity.UserFollow;
import roundtrip.user.domain.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;

    // ── 피드 조회 ──

    @Transactional(readOnly = true)
    public FeedResult getFeed(UUID userId, String feed, UUID cursorId, int limit) {
        List<CommunityPost> posts;
        if ("following".equals(feed)) {
            posts = communityRepository.findFollowingPostsBefore(userId, cursorId, limit + 1);
        } else {
            posts = communityRepository.findPublicPostsBefore(cursorId, limit + 1);
        }

        boolean hasMore = posts.size() > limit;
        if (hasMore) {
            posts = posts.subList(0, limit);
        }

        List<PostDetail> items = posts.stream()
            .map(p -> buildPostDetail(p, userId))
            .toList();

        UUID nextCursor = hasMore && !posts.isEmpty() ? posts.getLast().getId() : null;
        return new FeedResult(items, nextCursor);
    }

    // ── 포스트 작성 ──

    @Transactional
    public PostDetail createPost(UUID userId, String content, List<UUID> taggedPlaceIds, UUID taggedItineraryId) {
        CommunityPost post = CommunityPost.create(userId, content, "public");
        post = communityRepository.savePost(post);

        if (taggedPlaceIds != null) {
            for (UUID placeId : taggedPlaceIds) {
                communityRepository.saveTaggedPlace(PostTaggedPlace.of(post.getId(), placeId));
            }
        }
        if (taggedItineraryId != null) {
            communityRepository.saveTaggedItinerary(PostTaggedItinerary.of(post.getId(), taggedItineraryId));
        }

        return buildPostDetail(post, userId);
    }

    // ── 포스트 상세 ──

    @Transactional(readOnly = true)
    public PostDetail getPost(UUID postId, UUID userId) {
        CommunityPost post = findPostOrThrow(postId);
        return buildPostDetail(post, userId);
    }

    // ── 포스트 삭제 ──

    @Transactional
    public void deletePost(UUID postId, UUID userId) {
        CommunityPost post = findPostOrThrow(postId);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "타인의 포스트를 삭제할 수 없습니다.");
        }
        communityRepository.deletePost(post);
    }

    // ── 좋아요 ──

    @Transactional
    public int likePost(UUID postId, UUID userId) {
        CommunityPost post = findPostOrThrow(postId);
        if (communityRepository.existsLike(postId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        communityRepository.saveLike(PostLike.of(postId, userId));
        post.incrementLikeCount();
        communityRepository.savePost(post);
        return post.getLikeCount();
    }

    @Transactional
    public int unlikePost(UUID postId, UUID userId) {
        CommunityPost post = findPostOrThrow(postId);
        communityRepository.deleteLike(postId, userId);
        post.decrementLikeCount();
        communityRepository.savePost(post);
        return post.getLikeCount();
    }

    // ── 댓글 목록 ──

    @Transactional(readOnly = true)
    public CommentListResult getComments(UUID postId, UUID cursorId, int limit) {
        findPostOrThrow(postId);
        List<PostComment> comments = communityRepository.findCommentsByPostIdBefore(postId, cursorId, limit + 1);

        boolean hasMore = comments.size() > limit;
        if (hasMore) {
            comments = comments.subList(0, limit);
        }

        List<CommentDetail> items = comments.stream()
            .map(c -> {
                User author = userRepository.findById(c.getUserId()).orElse(null);
                return new CommentDetail(c, author);
            })
            .toList();

        UUID nextCursor = hasMore && !comments.isEmpty() ? comments.getLast().getId() : null;
        return new CommentListResult(items, nextCursor);
    }

    // ── 댓글 작성 ──

    @Transactional
    public CommentDetail createComment(UUID postId, UUID userId, String content) {
        CommunityPost post = findPostOrThrow(postId);
        PostComment comment = PostComment.create(postId, userId, content);
        comment = communityRepository.saveComment(comment);
        post.incrementCommentCount();
        communityRepository.savePost(post);

        User author = userRepository.findById(userId).orElse(null);
        return new CommentDetail(comment, author);
    }

    // ── 댓글 삭제 ──

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID userId) {
        findPostOrThrow(postId);
        PostComment comment = communityRepository.findCommentById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "타인의 댓글을 삭제할 수 없습니다.");
        }
        communityRepository.deleteComment(comment);

        communityRepository.findPostById(postId).ifPresent(post -> {
            post.decrementCommentCount();
            communityRepository.savePost(post);
        });
    }

    // ── 사용자 프로필 ──

    @Transactional(readOnly = true)
    public UserProfileResult getUserProfile(UUID targetUserId, UUID currentUserId) {
        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int followerCount = communityRepository.countFollowers(targetUserId);
        int followingCount = communityRepository.countFollowing(targetUserId);
        int postCount = communityRepository.countPostsByUserId(targetUserId);
        boolean isFollowing = communityRepository.isFollowing(currentUserId, targetUserId);

        return new UserProfileResult(user, followerCount, followingCount, postCount, isFollowing);
    }

    // ── 팔로우 ──

    @Transactional
    public void follow(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.SELF_FOLLOW);
        }
        userRepository.findById(followingId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (communityRepository.isFollowing(followerId, followingId)) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWING);
        }
        UserFollow follow = UserFollow.create(followerId, followingId);
        // Save via EntityManager since UserFollow is managed through the community repo's follow JPA
        communityRepository.saveFollow(follow);
    }

    @Transactional
    public void unfollow(UUID followerId, UUID followingId) {
        communityRepository.deleteFollow(followerId, followingId);
    }

    // ── helpers ──

    private CommunityPost findPostOrThrow(UUID postId) {
        return communityRepository.findPostById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private PostDetail buildPostDetail(CommunityPost post, UUID currentUserId) {
        User author = userRepository.findById(post.getUserId()).orElse(null);
        List<Place> taggedPlaces = communityRepository.findTaggedPlacesByPostId(post.getId());
        var taggedItinerary = communityRepository.findTaggedItineraryByPostId(post.getId()).orElse(null);
        boolean isLiked = communityRepository.existsLike(post.getId(), currentUserId);
        return new PostDetail(post, author, taggedPlaces, taggedItinerary, isLiked);
    }

    // ── result records ──

    public record PostDetail(
        CommunityPost post,
        User author,
        List<Place> taggedPlaces,
        CommunityRepository.TaggedItineraryInfo taggedItinerary,
        boolean isLiked
    ) {}

    public record FeedResult(List<PostDetail> items, UUID nextCursor) {}

    public record CommentDetail(PostComment comment, User author) {}

    public record CommentListResult(List<CommentDetail> items, UUID nextCursor) {}

    public record UserProfileResult(
        User user,
        int followerCount,
        int followingCount,
        int postCount,
        boolean isFollowing
    ) {}
}
