package roundtrip.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.auth.application.SignInResult;
import roundtrip.user.domain.entity.User;

import java.util.UUID;

@Schema(description = "소셜 로그인 응답")
public record SignInResponse(
    @Schema(description = "API 요청 시 사용하는 JWT 액세스 토큰")
    String accessToken,
    @Schema(description = "액세스 토큰 갱신용 토큰")
    String refreshToken,
    @Schema(description = "로그인한 사용자 정보")
    UserPayload user
) {

    public static SignInResponse from(SignInResult result) {
        return new SignInResponse(
            result.tokens().accessToken(),
            result.tokens().refreshToken(),
            UserPayload.from(result.user(), result.isNewUser())
        );
    }

    @Schema(description = "사용자 페이로드")
    public record UserPayload(
        @Schema(description = "사용자 고유 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,
        @Schema(description = "닉네임 (형용사+동물+숫자 형식)", example = "이상한 여우 8237")
        String nickname,
        @Schema(description = "프로필 이미지 URL", example = "https://cdn.example.com/avatars/fox.png")
        String avatarUrl,
        @Schema(description = "소셜 계정 이메일", example = "user@example.com")
        String email,
        @Schema(description = "언어 설정", example = "ko-KR")
        String locale,
        @Schema(description = "최초 가입 여부. true이면 온보딩 화면으로 이동", example = "true")
        boolean isNewUser,
        @Schema(description = "로그인 시점의 크레딧 잔액", example = "3")
        int creditBalance
    ) {

        public static UserPayload from(User user, boolean isNewUser) {
            return new UserPayload(
                user.getId(),
                user.getNickname().value(),
                user.getAvatarUrl(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getLocale(),
                isNewUser,
                user.getCreditBalance()
            );
        }
    }
}
