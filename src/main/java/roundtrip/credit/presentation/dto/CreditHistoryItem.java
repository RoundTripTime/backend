package roundtrip.credit.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.market.domain.entity.CreditHistory;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "크레딧 적립/차감 내역 항목")
public record CreditHistoryItem(
    @Schema(description = "내역 고유 ID")
    UUID historyId,
    @Schema(description = "크레딧 종류", example = "ad_view",
            allowableValues = {"ad_view", "ota_booking", "plan_sale", "plan_purchase", "ota_payment"})
    String creditType,
    @Schema(description = "적립/차감 수량. 적립은 양수, 차감은 음수", example = "1")
    int amount,
    @Schema(description = "해당 트랜잭션 후 크레딧 잔액", example = "4")
    int balanceAfter,
    @Schema(description = "내역 설명 텍스트", example = "광고 시청 보상")
    String description,
    @Schema(description = "발생 일시 (ISO 8601)", example = "2024-12-01T10:00:00Z")
    OffsetDateTime createdAt
) {
    public static CreditHistoryItem from(CreditHistory creditHistory){
        return new CreditHistoryItem(
            creditHistory.getId(),
            creditHistory.getCreditType(),
            creditHistory.getAmount(),
            creditHistory.getBalanceAfter(),
            creditHistory.getDescription(),
            creditHistory.getCreatedAt()
        );
    }
}

