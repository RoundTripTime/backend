package roundtrip.user.presentation;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.user.application.UpdateProfileCommand;
import roundtrip.user.application.UserService;
import roundtrip.user.presentation.dto.MyProfileResponse;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.of(SuccessCode.USER_PROFILE_FETCHED,
                MyProfileResponse.from(userService.getMyProfile(principal.userId())));
    }

    @PatchMapping
    public ResponseEntity<MyProfileResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileCommand command) {
        return ApiResponse.of(SuccessCode.USER_PROFILE_UPDATED,
                MyProfileResponse.from(userService.updateMyProfile(principal.userId(), command)));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        userService.withdraw(principal.userId());
        return ApiResponse.noContent(SuccessCode.USER_ACCOUNT_DELETED);
    }
}
