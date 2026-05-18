package roundtrip.auth.presentation;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import roundtrip.auth.application.AuthService;
import roundtrip.auth.application.SignInResult;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.auth.domain.IssuedTokens;
import roundtrip.auth.presentation.dto.RefreshRequest;
import roundtrip.auth.presentation.dto.RefreshResponse;
import roundtrip.auth.presentation.dto.SignInRequest;
import roundtrip.auth.presentation.dto.SignInResponse;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;

import java.util.Locale;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/social")
    public ResponseEntity<SignInResponse> signInWithSocial(
            @Valid @RequestBody SignInRequest request, Locale clientLocale) {
        SignInResult result = authService.signInWithSocial(request.provider(), request.idToken(), clientLocale);
        return ApiResponse.of(SuccessCode.AUTH_LOGIN_SUCCESS, SignInResponse.from(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request) {
        IssuedTokens tokens = authService.refresh(request.refreshToken());
        return ApiResponse.of(SuccessCode.AUTH_TOKEN_REFRESHED, RefreshResponse.from(tokens));
    }

    @DeleteMapping("/session")
    @SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(principal.userId());
        return ApiResponse.noContent(SuccessCode.AUTH_LOGOUT_SUCCESS);
    }
}
