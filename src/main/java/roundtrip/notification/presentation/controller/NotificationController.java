package roundtrip.notification.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import roundtrip.auth.domain.AuthenticatedUser;
import roundtrip.common.config.SwaggerConfig;
import roundtrip.common.exception.ErrorResponse;
import roundtrip.common.response.ApiResponse;
import roundtrip.common.response.SuccessCode;
import roundtrip.notification.application.NotificationService;
import roundtrip.notification.domain.entity.Notification;
import roundtrip.notification.presentation.dto.*;

import java.util.UUID;

@Tag(name = "Notifications", description = "알림 — 목록 조회, 읽음 처리")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/notifications")
    public ResponseEntity<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "is_read", required = false) Boolean isRead,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID cursor) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        var result = notificationService.getNotifications(user.userId(), isRead, cursor, safeLimit);
        return ApiResponse.of(SuccessCode.NOTIFICATION_LIST_FETCHED, NotificationListResponse.from(result));
    }

    @Operation(summary = "알림 읽음 처리")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOT_FOUND",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<NotificationReadResponse> markAsRead(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID notificationId) {
        Notification notification = notificationService.markAsRead(notificationId, user.userId());
        return ApiResponse.of(SuccessCode.NOTIFICATION_READ,
            new NotificationReadResponse(notification.getId(), notification.isRead()));
    }
}
