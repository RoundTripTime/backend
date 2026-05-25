package roundtrip.auth.domain;

public record IssuedTokens(String accessToken, String refreshToken, String refreshJti) {
}
