package roundtrip.collection.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CollectionPlaceId implements Serializable {

    @Column(name = "collection_id", columnDefinition = "uuid")
    private UUID collectionId;

    @Column(name = "place_id", columnDefinition = "uuid")
    private UUID placeId;
}
