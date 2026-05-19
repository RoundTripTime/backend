package roundtrip.collection.presentation.dto;

public record UpdateCollectionRequest(
        String name,
        String icon,
        String visibility
) {}
