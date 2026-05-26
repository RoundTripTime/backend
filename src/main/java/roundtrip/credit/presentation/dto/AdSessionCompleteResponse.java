package roundtrip.credit.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.credit.application.AdSessionService;

@Schema(description = "광고 시청 완료 응답")
public record AdSessionCompleteResponse(
    @Schema(description = "오늘 시청 완료한 광고 수 (이번 건 포함)", example = "3")
    int viewedToday,

    @Schema(description = "크레딧 1개 적립에 필요한 총 시청 수", example = "5")
    int requiredForCredit,

    @Schema(description = "이번 완료로 크레딧 1개가 적립됐는지 여부", example = "false")
    boolean creditEarned,

    @Schema(description = "적립 후 현재 크레딧 잔액", example = "3")
    int balance
) {
    public static AdSessionCompleteResponse from(AdSessionService.CompleteResult result){
        return new AdSessionCompleteResponse(
            result.viewedToday(),
            result.requiredForCredit(),
            result.creditEarned(),
            result.balance()
        );
    }
}
