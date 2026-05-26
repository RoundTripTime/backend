package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OTA 예약 링크 응답")
public record OtaLinkResponse(
    @Schema(description = "OTA 예약 링크 URL")
    String otaUrl
) {}
