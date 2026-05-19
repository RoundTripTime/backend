package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유 링크 응답")
public record CollectionShareResponse(
    @Schema(description = "공유 URL", example = "https://app.example.com/share/collections/abc123")
    String shareUrl,
    @Schema(description = "현재 공개 범위", example = "public", allowableValues = {"public", "private"})
    String visibility
) {}
