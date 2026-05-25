package roundtrip.collection.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "플레이스에 장소 추가 요청")
public record AddPlaceToCollectionRequest(
    @Schema(description = "추가할 장소 ID")
    @NotNull UUID placeId
) {}
