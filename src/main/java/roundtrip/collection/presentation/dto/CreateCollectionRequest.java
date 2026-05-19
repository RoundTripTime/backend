package roundtrip.collection.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCollectionRequest(
        @NotBlank @Size(max = 100) String name,
        String icon
) {}
