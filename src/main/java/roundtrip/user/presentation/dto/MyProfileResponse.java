package roundtrip.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.user.domain.entity.User;

import java.time.OffsetDateTime;

@Schema(description = "내 프로필 응답")
public record MyProfileResponse(
    @Schema(description = "사용자 고유 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    String id,
    @Schema(description = "닉네임", example = "이상한 여우 8237")
    String nickname,
    @Schema(description = "프로필 이미지 URL", example = "https://cdn.example.com/avatars/fox.png")
    String avatarUrl,
    @Schema(description = "이메일", example = "user@example.com")
    String email,
    @Schema(description = "언어 설정", example = "ko-KR")
    String locale,
    @Schema(description = "선호 지역", example = "서울")
    String homeRegion,
    @Schema(description = "선호 지도 앱", example = "kakao", allowableValues = {"kakao", "google"})
    String mapProvider,
    @Schema(description = "현재 크레딧 잔액", example = "3")
    int creditBalance,
    @Schema(description = "계정 생성 일시 (ISO 8601)", example = "2024-01-01T00:00:00Z")
    OffsetDateTime createdAt
) {
    public static MyProfileResponse from(User user){
        return new MyProfileResponse(
            user.getId().toString(),
            user.getNickname().value(),
            user.getAvatarUrl(),
            user.getEmail() == null ? null : user.getEmail().value(),
            user.getLocale(),
            user.getHomeRegion(),
            user.getMapProvider().name().toLowerCase(),
            user.getCreditBalance(),
            user.getCreatedAt()
        );
    }
}
