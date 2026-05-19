package roundtrip.sourcelink.presentation.dto;

import java.util.List;
import java.util.UUID;

public record SourceLinkListResponse(
        List<SourceLinkItem> items,
        UUID nextCursor
) {
}
