package roundtrip.auth.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;

import java.util.Locale;

@Tag(name = "Auth", description = "인증 — 소셜 로그인, 토큰 갱신, 로그아웃")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "소셜 로그인", description = "Google / Kakao OAuth ID 토큰으로 가입 또는 로그인한다. 최초 가입 시 `user.is_new_user = true`.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 필수 파라미터 누락",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/social")
    public ResponseEntity<SignInResponse> signInWithSocial(
            @Valid @RequestBody SignInRequest request, Locale clientLocale) {
        SignInResult result = authService.signInWithSocial(request.provider(), request.idToken(), clientLocale);
        return ApiResponse.of(SuccessCode.AUTH_LOGIN_SUCCESS, SignInResponse.from(result));
    }

    @Operation(summary = "액세스 토큰 갱신", description = "리프레시 토큰으로 새 액세스 토큰을 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED — 유효하지 않거나 만료된 리프레시 토큰",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request) {
        IssuedTokens tokens = authService.refresh(request.refreshToken());
        return ApiResponse.of(SuccessCode.AUTH_TOKEN_REFRESHED, RefreshResponse.from(tokens));
    }

    @Operation(summary = "로그아웃", description = "서버 측 리프레시 토큰을 무효화한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/session")
    @SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logout(principal.userId());
        return ApiResponse.noContent(SuccessCode.AUTH_LOGOUT_SUCCESS);
    }
}
