package roundtrip.auth.presentation.dto;

import roundtrip.auth.domain.IssuedTokens;

public record RefreshResponse(String accessToken, String refreshToken) {

    public static RefreshResponse from(IssuedTokens tokens) {
        return new RefreshResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
