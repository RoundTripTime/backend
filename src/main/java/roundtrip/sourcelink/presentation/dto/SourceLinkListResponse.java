package roundtrip.sourcelink.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "링크 목록 응답")
public record SourceLinkListResponse(
    @Schema(description = "링크 목록")
    List<SourceLinkItem> items,
    @Schema(description = "다음 페이지 커서. null이면 마지막 페이지")
    UUID nextCursor
) {
}
