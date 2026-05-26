package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "일정 순서 일괄 변경 요청")
public record ReorderItemsRequest(
    @Schema(description = "재배열할 아이템 목록")
    @NotEmpty @Valid List<ReorderEntry> items
) {
    @Schema(description = "재배열 항목")
    public record ReorderEntry(
        @Schema(description = "아이템 ID")
        @NotNull UUID itemId,
        @Schema(description = "변경될 일자")
        @NotNull Integer dayIndex,
        @Schema(description = "변경될 순서")
        @NotNull Integer sortOrder
    ) {}
}
