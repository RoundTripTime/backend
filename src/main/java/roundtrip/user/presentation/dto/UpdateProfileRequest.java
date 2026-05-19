package roundtrip.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 수정 요청 (변경할 항목만 포함)")
public record UpdateProfileRequest(
    @Schema(description = "닉네임 (최대 20자)", example = "이상한 여우 8237", nullable = true)
    @Size(max = 20, message = "닉네임은 최대 20자입니다.")
    String nickname,

    @Schema(description = "프로필 사진 URL", nullable = true)
    String avatarUrl,

    @Schema(description = "선호 지역", example = "서울", nullable = true)
    String homeRegion,

    @Schema(description = "언어 설정", example = "ko-KR", nullable = true)
    String locale,

    @Schema(description = "선호 지도 앱", example = "kakao", allowableValues = {"kakao", "google"}, nullable = true)
    @Pattern(regexp = "kakao|google", message = "kakao 또는 google만 가능합니다")
    String mapProvider
) {}
