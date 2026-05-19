package roundtrip.candidate.presentation.dto;

import java.util.List;

public record CandidateListResponse(
        SourceLinkSummary sourceLink,
        List<CandidateResponse> candidates
) {
}
