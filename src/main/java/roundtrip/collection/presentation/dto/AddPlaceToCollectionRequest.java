package roundtrip.collection.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddPlaceToCollectionRequest(@NotNull UUID placeId) {}
