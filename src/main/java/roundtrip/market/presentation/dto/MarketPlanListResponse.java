package roundtrip.market.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.market.application.MarketService.MarketListResult;

import java.util.List;
import java.util.UUID;

@Schema(description = "플랜 마켓 목록 응답")
public record MarketPlanListResponse(
    @Schema(description = "플랜 목록") List<MarketPlanCardResponse> items,
    @Schema(description = "다음 페이지 커서", nullable = true) UUID nextCursor
) {
    public static MarketPlanListResponse from(MarketListResult result) {
        List<MarketPlanCardResponse> items = result.items().stream()
            .map(MarketPlanCardResponse::from)
            .toList();
        return new MarketPlanListResponse(items, result.nextCursor());
    }
}
