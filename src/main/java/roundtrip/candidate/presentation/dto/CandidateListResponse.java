package roundtrip.candidate.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "장소 후보 목록 응답")
public record CandidateListResponse(
    @Schema(description = "분석된 원본 링크 정보")
    SourceLinkSummary sourceLink,
    @Schema(description = "추출된 장소 후보 목록")
    List<CandidateResponse> candidates
) {
}
