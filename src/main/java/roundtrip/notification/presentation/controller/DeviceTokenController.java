package roundtrip.notification.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.notification.application.DeviceTokenService;
import roundtrip.notification.domain.entity.DeviceToken;
import roundtrip.notification.presentation.dto.RegisterDeviceTokenRequest;
import roundtrip.notification.presentation.dto.RegisterDeviceTokenResponse;
import roundtrip.notification.presentation.dto.UnregisterDeviceTokenRequest;

@Tag(name = "Notifications", description = "알림 — FCM 디바이스 토큰 등록/해제")
@RestController
@RequestMapping("/notifications/device-tokens")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "FCM 디바이스 토큰 등록", description = "동일 토큰이 이미 있으면 현재 사용자로 갱신합니다.")
    @PostMapping
    public ResponseEntity<RegisterDeviceTokenResponse> register(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        DeviceToken deviceToken = deviceTokenService.register(user.userId(), request.token(), request.platform());
        return ApiResponse.of(SuccessCode.DEVICE_TOKEN_REGISTERED,
                new RegisterDeviceTokenResponse(deviceToken.getId()));
    }

    @Operation(summary = "FCM 디바이스 토큰 해제", description = "로그아웃 등으로 더 이상 푸시를 받지 않을 때 호출합니다.")
    @DeleteMapping
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UnregisterDeviceTokenRequest request) {
        deviceTokenService.unregister(user.userId(), request.token());
        return ApiResponse.noContent(SuccessCode.DEVICE_TOKEN_DELETED);
    }
}
