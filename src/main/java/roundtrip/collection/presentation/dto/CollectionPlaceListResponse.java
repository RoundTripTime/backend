package roundtrip.collection.presentation.dto;

import roundtrip.collection.application.CollectionService.CollectionWithPlaces;
import roundtrip.collection.domain.entity.Collection;

import java.util.List;
import java.util.UUID;

public record CollectionPlaceListResponse(
        UUID collectionId,
        String name,
        String visibility,
        List<CollectionPlaceItem> places
) {
    public static CollectionPlaceListResponse from(CollectionWithPlaces result) {
        Collection c = result.collection();
        List<CollectionPlaceItem> items = result.places().stream()
                .map(CollectionPlaceItem::from)
                .toList();
        return new CollectionPlaceListResponse(c.getId(), c.getName(), c.getVisibility(), items);
    }
}
