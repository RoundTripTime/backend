package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "유사 장소 추천 응답")
public record PlaceSimilarResponse(
    @Schema(description = "유사 장소 목록")
    List<PlaceSimilarResult> results
) {}
