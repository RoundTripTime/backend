package roundtrip.place.domain.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceThumbnailTest {

    @Test
    void updateThumbnail_setsUrlAndSource() {
        Place place = Place.create("남산타워", BigDecimal.valueOf(37.5512),
                BigDecimal.valueOf(126.9882), PlaceCategory.ATTRACTION, "KR", "99999", null);

        place.updateThumbnail("https://example.com/img.jpg", ThumbnailSource.WIKIMEDIA);

        assertThat(place.getThumbnailUrl()).isEqualTo("https://example.com/img.jpg");
        assertThat(place.getThumbnailSource()).isEqualTo(ThumbnailSource.WIKIMEDIA);
    }

    @Test
    void updateThumbnail_withNone_setsSourceOnly() {
        Place place = Place.create("남산타워", BigDecimal.valueOf(37.5512),
                BigDecimal.valueOf(126.9882), PlaceCategory.ATTRACTION, "KR", "99999", null);

        place.updateThumbnail(null, ThumbnailSource.NONE);

        assertThat(place.getThumbnailUrl()).isNull();
        assertThat(place.getThumbnailSource()).isEqualTo(ThumbnailSource.NONE);
    }

    @Test
    void create_initialState_thumbnailFieldsAreNull() {
        Place place = Place.create("카페", BigDecimal.ONE, BigDecimal.ONE,
                PlaceCategory.CAFE, "KR", "11111", null);

        assertThat(place.getThumbnailUrl()).isNull();
        assertThat(place.getThumbnailSource()).isNull();
    }
}
