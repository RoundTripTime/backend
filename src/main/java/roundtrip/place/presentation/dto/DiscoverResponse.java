package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "둘러보기 추천 응답")
public record DiscoverResponse(
    @Schema(description = "추천 장소 목록")
    List<DiscoverResult> results
) {}
