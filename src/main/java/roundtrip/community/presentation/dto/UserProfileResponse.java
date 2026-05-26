package roundtrip.community.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.community.application.CommunityService.UserProfileResult;

import java.util.UUID;

@Schema(description = "사용자 프로필 응답")
public record UserProfileResponse(
    @Schema(description = "사용자 고유 ID") UUID userId,
    @Schema(description = "닉네임") String nickname,
    @Schema(description = "프로필 이미지 URL") String avatarUrl,
    @Schema(description = "팔로워 수") int followerCount,
    @Schema(description = "팔로잉 수") int followingCount,
    @Schema(description = "포스트 수") int postCount,
    @Schema(description = "현재 로그인 사용자의 팔로우 여부") boolean isFollowing
) {
    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(
            result.user().getId(),
            result.user().getNickname().value(),
            result.user().getAvatarUrl(),
            result.followerCount(),
            result.followingCount(),
            result.postCount(),
            result.isFollowing()
        );
    }
}
