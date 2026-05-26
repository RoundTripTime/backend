package roundtrip.credit.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.credit.application.CreditService;

@Schema(description = "내 크레딧 잔액 조회 응답")
public record CreditBalanceResponse(
    @Schema(description = "현재 사용 가능한 크레딧 잔액", example = "3")
    int balance,

    @Schema(description = "가입 이후 누적 적립 크레딧 총합", example = "25")
    long lifetimeEarned,

    @Schema(description = "가입 이후 누적 차감 크레딧 총합", example = "22")
    long lifetimeSpent
) {
    public static CreditBalanceResponse from(CreditService.MyCreditResult result) {
        return new CreditBalanceResponse(result.balance(), result.lifetimeEarned(), result.lifetimeSpent());
    }
}
