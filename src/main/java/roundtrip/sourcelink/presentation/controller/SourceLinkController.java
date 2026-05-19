package roundtrip.sourcelink.presentation.controller;

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
import roundtrip.sourcelink.application.SourceLinkService;
import roundtrip.sourcelink.domain.entity.LinkStatus;
import roundtrip.sourcelink.presentation.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/source-links")
@RequiredArgsConstructor
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME_NAME)
public class SourceLinkController {

    private final SourceLinkService sourceLinkService;

    @PostMapping
    public ResponseEntity<SubmitSourceLinkResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SubmitSourceLinkRequest request) {
        SourceLinkService.SubmitResult result = sourceLinkService.submit(principal.userId(), request.url());
        return ApiResponse.of(SuccessCode.SOURCE_LINK_SUBMITTED, SubmitSourceLinkResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<SourceLinkListResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID cursor) {
        LinkStatus linkStatus = null;
        if (status != null) {
            try {
                linkStatus = LinkStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<SourceLinkService.SourceLinkWithJob> items =
                sourceLinkService.listByUser(principal.userId(), linkStatus, limit, cursor);

        UUID nextCursor = null;
        if (items.size() == limit) {
            nextCursor = items.get(items.size() - 1).sourceLink().getId();
        }

        List<SourceLinkItem> dtoItems = items.stream()
                .map(r -> SourceLinkItem.of(r.sourceLink(), r.jobStatus()))
                .toList();
        return ApiResponse.of(SuccessCode.SOURCE_LINK_LIST_FETCHED, new SourceLinkListResponse(dtoItems, nextCursor));
    }
}
