package roundtrip.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.auth.domain.IssuedTokens;

@Schema(description = "토큰 갱신 응답")
public record RefreshResponse(
    @Schema(description = "새로 발급된 JWT 액세스 토큰", example = "eyJhbGci...")
    String accessToken,
    @Schema(description = "새로 발급된 리프레시 토큰", example = "eyJhbGci...")
    String refreshToken
) {

    public static RefreshResponse from(IssuedTokens tokens) {
        return new RefreshResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
