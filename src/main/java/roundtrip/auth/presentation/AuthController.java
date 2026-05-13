package roundtrip.auth.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import roundtrip.auth.application.AuthService;
import roundtrip.auth.application.SignInResult;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.auth.domain.IssuedTokens;
import roundtrip.auth.presentation.dto.RefreshRequest;
import roundtrip.auth.presentation.dto.RefreshResponse;
import roundtrip.auth.presentation.dto.SignInRequest;
import roundtrip.auth.presentation.dto.SignInResponse;

import java.util.Locale;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/social")
    @ResponseStatus(HttpStatus.CREATED)
    public SignInResponse signInWithSocial(@Valid @RequestBody SignInRequest request, Locale clientLocale){
        SignInResult result = authService.signInWithSocial(request.provider(), request.idToken(), clientLocale);
        return SignInResponse.from(result);
    }


    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request){
        IssuedTokens tokens = authService.refresh(request.refreshToken());
        return RefreshResponse.from(tokens);
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedUser principal){
        authService.logout(principal.userId());
    }
}
