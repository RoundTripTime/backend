package roundtrip.collection.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collection_places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionPlace {

    @EmbeddedId
    private CollectionPlaceId id;

    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt;

    public static CollectionPlace of(UUID collectionId, UUID placeId) {
        CollectionPlace cp = new CollectionPlace();
        cp.id = new CollectionPlaceId(collectionId, placeId);
        cp.addedAt = OffsetDateTime.now();
        return cp;
    }

    public UUID getCollectionId() {
        return id.getCollectionId();
    }

    public UUID getPlaceId() {
        return id.getPlaceId();
    }
}
