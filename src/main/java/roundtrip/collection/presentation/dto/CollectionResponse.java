package roundtrip.collection.presentation.dto;

import roundtrip.collection.domain.entity.Collection;

import java.util.UUID;

public record CollectionResponse(
        UUID collectionId,
        String name,
        boolean isDefault,
        String icon,
        String visibility
) {
    public static CollectionResponse from(Collection c) {
        return new CollectionResponse(
                c.getId(),
                c.getName(),
                c.isDefault(),
                c.getIcon(),
                c.getVisibility()
        );
    }
}
