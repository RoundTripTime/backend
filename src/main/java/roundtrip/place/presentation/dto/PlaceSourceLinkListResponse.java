package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "장소 출처 영상 목록 응답")
public record PlaceSourceLinkListResponse(
    @Schema(description = "출처 영상 목록")
    List<PlaceSourceLinkItem> items
) {}
