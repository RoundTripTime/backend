package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "장소 검색 응답")
public record PlaceSearchResponse(
    @Schema(description = "검색 결과 장소 목록")
    List<PlaceSearchResult> results
) {}
