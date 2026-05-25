package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "플레이스 목록 응답")
public record CollectionListResponse(
    @Schema(description = "플레이스 목록")
    List<CollectionItem> items
) {}
