package roundtrip.sourcelink.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitSourceLinkRequest(
        @NotBlank String url
) {
}
