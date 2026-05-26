package roundtrip.credit.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record CreditHistoryListResponse(
    @Schema(description = "내역 목록")
    List<CreditHistoryItem> items,
    @Schema(description = "다음 페이지 커서. null이면 마지막 페이지")
    UUID nextCursor
) {
}
