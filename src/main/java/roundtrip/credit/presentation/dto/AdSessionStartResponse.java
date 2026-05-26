package roundtrip.credit.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.credit.application.AdSessionService;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdSessionStartResponse(
    @Schema(description = "광고 세션 고유 ID. complete 호출 시 필요")
    UUID adSessionId,

    @Schema(description = "세션 만료 일시 (ISO 8601)", example = "2024-12-01T10:05:00Z")
    OffsetDateTime expiresAt,

    @Schema(description = "오늘 시청 완료한 광고 수 (이번 건 미포함)", example = "2")
    int viewedToday,

    @Schema(description = "크레딧 1개 적립에 필요한 총 시청 수", example = "5")
    int requiredForCredit
) {
    public static AdSessionStartResponse from(AdSessionService.StartResult result){
        return new AdSessionStartResponse(
            result.adSessionId(),
            result.expiresAt(),
            result.viewedToday(),
            result.requiredForCredit()
        );
    }
}
