package roundtrip.auth.application;

import roundtrip.auth.domain.IssuedTokens;
import roundtrip.user.domain.entity.User;

public record SignInResult(User user, IssuedTokens tokens, boolean isNewUser) {
}
