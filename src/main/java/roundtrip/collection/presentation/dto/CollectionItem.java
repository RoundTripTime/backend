package roundtrip.collection.presentation.dto;

import roundtrip.collection.application.CollectionService.CollectionSummary;
import roundtrip.collection.domain.entity.Collection;

import java.util.UUID;

public record CollectionItem(
        UUID collectionId,
        String name,
        boolean isDefault,
        String icon,
        int placeCount,
        String visibility
) {
    public static CollectionItem from(CollectionSummary summary) {
        Collection c = summary.collection();
        return new CollectionItem(
                c.getId(),
                c.getName(),
                c.isDefault(),
                c.getIcon(),
                summary.placeCount(),
                c.getVisibility()
        );
    }
}
