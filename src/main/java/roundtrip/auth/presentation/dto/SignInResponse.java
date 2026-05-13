package roundtrip.auth.presentation.dto;

import roundtrip.auth.application.SignInResult;
import roundtrip.user.domain.entity.User;

import java.util.UUID;

public record SignInResponse(
    String accessToken,
    String refreshToken,
    UserPayload user
) {

    public static SignInResponse from(SignInResult result) {
        return new SignInResponse(
            result.tokens().accessToken(),
            result.tokens().refreshToken(),
            UserPayload.from(result.user(), result.isNewUser())
        );
    }

    public record UserPayload(
        UUID id,
        String nickname,
        String avatarUrl,
        String email,
        String locale,
        boolean isNewUser,
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
