package roundtrip.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import roundtrip.user.domain.entity.SocialProvider;

@Schema(description = "소셜 로그인 요청")
public record SignInRequest(
    @Schema(description = "소셜 제공자", example = "google", allowableValues = {"google", "kakao"})
    @NotNull SocialProvider provider,
    @Schema(description = "소셜 제공자가 발급한 ID 토큰", example = "eyJhbGci...")
    @NotBlank String idToken
) {
}
