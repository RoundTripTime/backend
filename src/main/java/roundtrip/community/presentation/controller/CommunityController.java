package roundtrip.community.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.community.application.CommunityService;
import roundtrip.community.presentation.dto.*;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;

import java.util.UUID;

@Tag(name = "Community", description = "커뮤니티 — 포스트, 좋아요, 댓글, 프로필, 팔로우")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class CommunityController {

    private final CommunityService communityService;

    // ──────────────────── Posts ────────────────────

    @Operation(summary = "커뮤니티 피드 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/community/posts")
    public ResponseEntity<PostFeedResponse> getFeed(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "all") String feed,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID cursor) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var result = communityService.getFeed(user.userId(), feed, cursor, safeLimit);
        return ApiResponse.of(SuccessCode.POST_LIST_FETCHED, PostFeedResponse.from(result));
    }

    @Operation(summary = "포스트 작성")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "작성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/community/posts")
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreatePostRequest request) {
        var detail = communityService.createPost(
            user.userId(), request.content(), request.taggedPlaceIds(), request.taggedItineraryId());
        return ApiResponse.of(SuccessCode.POST_CREATED, PostResponse.from(detail));
    }

    @Operation(summary = "포스트 상세 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/community/posts/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID postId) {
        var detail = communityService.getPost(postId, user.userId());
        return ApiResponse.of(SuccessCode.POST_FETCHED, PostResponse.from(detail));
    }

    @Operation(summary = "포스트 삭제", description = "본인 포스트만 삭제 가능.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN — 타인 포스트 삭제 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/community/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID postId) {
        communityService.deletePost(postId, user.userId());
        return ApiResponse.noContent(SuccessCode.POST_DELETED);
    }

    // ──────────────────── Likes ────────────────────

    @Operation(summary = "포스트 좋아요")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "좋아요 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CONFLICT — 이미 좋아요한 포스트",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/community/posts/{postId}/like")
    public ResponseEntity<LikeCountResponse> likePost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID postId) {
        int likeCount = communityService.likePost(postId, user.userId());
        return ApiResponse.of(SuccessCode.POST_LIKE_CREATED, new LikeCountResponse(likeCount));
    }

    @Operation(summary = "포스트 좋아요 취소")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 취소 성공")
    })
    @DeleteMapping("/community/posts/{postId}/like")
    public ResponseEntity<LikeCountResponse> unlikePost(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID postId) {
        int likeCount = communityService.unlikePost(postId, user.userId());
        return ApiResponse.of(SuccessCode.POST_LIKE_REMOVED, new LikeCountResponse(likeCount));
    }

    // ──────────────────── Comments ────────────────────

    @Operation(summary = "댓글 목록 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/community/posts/{postId}/comments")
    public ResponseEntity<CommentListResponse> getComments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID cursor) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var result = communityService.getComments(postId, cursor, safeLimit);
        return ApiResponse.of(SuccessCode.COMMENT_LIST_FETCHED, CommentListResponse.from(result));
    }

    @Operation(summary = "댓글 작성")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "작성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND — 포스트 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/community/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        var detail = communityService.createComment(postId, user.userId(), request.content());
        return ApiResponse.of(SuccessCode.COMMENT_CREATED, CommentResponse.from(detail));
    }

    @Operation(summary = "댓글 삭제", description = "본인 댓글만 삭제 가능.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN — 타인 댓글 삭제 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/community/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        communityService.deleteComment(postId, commentId, user.userId());
        return ApiResponse.noContent(SuccessCode.COMMENT_DELETED);
    }

    // ──────────────────── Profile & Follow ────────────────────

    @Operation(summary = "다른 사용자 프로필 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID userId) {
        var result = communityService.getUserProfile(userId, user.userId());
        return ApiResponse.of(SuccessCode.USER_PUBLIC_PROFILE_FETCHED, UserProfileResponse.from(result));
    }

    @Operation(summary = "사용자 팔로우")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "팔로우 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN — 본인 팔로우 시도",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CONFLICT — 이미 팔로우 중",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/users/{userId}/follow")
    public ResponseEntity<Void> follow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID userId) {
        communityService.follow(user.userId(), userId);
        return ApiResponse.of(SuccessCode.USER_FOLLOW_CREATED, null);
    }

    @Operation(summary = "사용자 언팔로우")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "언팔로우 성공")
    })
    @DeleteMapping("/users/{userId}/follow")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID userId) {
        communityService.unfollow(user.userId(), userId);
        return ApiResponse.noContent(SuccessCode.USER_UNFOLLOW_SUCCESS);
    }
}
