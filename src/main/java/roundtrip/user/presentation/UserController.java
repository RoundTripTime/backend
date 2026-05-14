package roundtrip.user.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.user.application.UpdateProfileCommand;
import roundtrip.user.application.UserService;
import roundtrip.user.presentation.dto.MyProfileResponse;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public MyProfileResponse getMyProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return MyProfileResponse.from(userService.getMyProfile(principal.userId()));
    }

    @PatchMapping
    public MyProfileResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody UpdateProfileCommand command) {
        return MyProfileResponse.from(userService.updateMyProfile(principal.userId(), command));
    }

    //@DeleteMapping 삭제해도 이상 없는지 확인 로직 필요

}
