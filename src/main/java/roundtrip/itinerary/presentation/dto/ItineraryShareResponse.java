package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플랜 공유 링크 응답")
public record ItineraryShareResponse(
    @Schema(description = "공유 URL", example = "https://app.example.com/share/itineraries/abc123")
    String shareUrl,
    @Schema(description = "현재 공개 범위", allowableValues = {"public", "private"})
    String visibility
) {}
