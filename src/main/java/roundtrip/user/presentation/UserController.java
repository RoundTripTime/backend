package roundtrip.user.presentation;

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
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.user.application.UpdateProfileCommand;
import roundtrip.user.application.UserService;
import roundtrip.user.presentation.dto.MyProfileResponse;
import roundtrip.user.presentation.dto.UpdateProfileRequest;

@Tag(name = "Users", description = "사용자 — 내 프로필 조회/수정, 계정 삭제")
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필 조회", description = "인증된 사용자의 프로필 정보를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.of(SuccessCode.USER_PROFILE_FETCHED,
                MyProfileResponse.from(userService.getMyProfile(principal.userId())));
    }

    @Operation(summary = "프로필 수정", description = "변경할 항목만 포함 — 모든 필드 optional.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping
    public ResponseEntity<MyProfileResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        UpdateProfileCommand command = new UpdateProfileCommand(
                request.nickname(), request.avatarUrl(), request.homeRegion(),
                request.locale(), request.mapProvider());
        return ApiResponse.of(SuccessCode.USER_PROFILE_UPDATED,
                MyProfileResponse.from(userService.updateMyProfile(principal.userId(), command)));
    }

    @Operation(summary = "계정 삭제", description = "사용자 계정과 관련 데이터를 완전히 삭제한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        userService.withdraw(principal.userId());
        return ApiResponse.noContent(SuccessCode.USER_ACCOUNT_DELETED);
    }
}
