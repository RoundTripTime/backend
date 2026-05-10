package roundtrip.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import roundtrip.user.domain.entity.SocialProvider;

public record SignInRequest(
    @NotNull SocialProvider provider,
    @NotBlank String idToken
) {
}
