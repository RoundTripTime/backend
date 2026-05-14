package roundtrip.user.presentation.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 20, message = "닉네임은 최대 20자입니다.")
    String nickname,
    String avatarUrl,
    String homeRegion,
    String locale,
    @Pattern(regexp = "kakao|google", message = "kakao 또는 google만 가능합니다")
    String mapProvider
) {
}
