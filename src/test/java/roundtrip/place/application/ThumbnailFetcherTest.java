package roundtrip.place.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.entity.PlaceCategory;
import roundtrip.place.domain.entity.ThumbnailSource;
import roundtrip.place.domain.entity.ThumbnailImage;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.place.infrastructure.external.GooglePlacesClient;
import roundtrip.place.infrastructure.external.WikimediaClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThumbnailFetcherTest {

    @Mock WikimediaClient wikimediaClient;
    @Mock GooglePlacesClient googlePlacesClient;
    @Mock PlaceRepository placeRepository;

    @InjectMocks ThumbnailFetcher thumbnailFetcher;

    private UUID placeId;
    private Place place;

    @BeforeEach
    void setUp() {
        placeId = UUID.randomUUID();
        place = Place.create("경복궁", BigDecimal.valueOf(37.5796), BigDecimal.valueOf(126.9770),
                PlaceCategory.ATTRACTION, "KR", "12345", null);
        ReflectionTestUtils.setField(place, "id", placeId);
    }

    @Test
    void fetchAndUpdate_wikimediaSuccess_setsThumbnailFromWikimedia() {
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(wikimediaClient.fetchImage("경복궁"))
                .thenReturn(Optional.of(new ThumbnailImage(
                        "https://upload.wikimedia.org/thumb/gyeongbokgung.jpg",
                        "Photographer", "CC BY-SA 4.0",
                        "https://creativecommons.org/licenses/by-sa/4.0",
                        "https://commons.wikimedia.org/wiki/File:test.jpg")));

        thumbnailFetcher.fetchAndUpdate(placeId);

        assertThat(place.getThumbnailUrl()).isEqualTo("https://upload.wikimedia.org/thumb/gyeongbokgung.jpg");
        assertThat(place.getThumbnailSource()).isEqualTo(ThumbnailSource.WIKIMEDIA);
        assertThat(place.getThumbnailAttribution()).isEqualTo("Photographer");
        assertThat(place.getThumbnailLicense()).isEqualTo("CC BY-SA 4.0");
        assertThat(place.getThumbnailLicenseUrl())
                .isEqualTo("https://creativecommons.org/licenses/by-sa/4.0");
        verify(placeRepository).save(place);
        verify(googlePlacesClient, never()).fetchImage(any());
    }

    @Test
    void fetchAndUpdate_wikimediaFails_fallsBackToGooglePlaces() {
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(wikimediaClient.fetchImage("경복궁")).thenReturn(Optional.empty());
        when(googlePlacesClient.fetchImage("경복궁"))
                .thenReturn(Optional.of(new ThumbnailImage(
                        "https://lh3.googleusercontent.com/photo", "Contributor", null, null,
                        "https://www.google.com/maps/contrib/example")));

        thumbnailFetcher.fetchAndUpdate(placeId);

        assertThat(place.getThumbnailUrl()).isEqualTo("https://lh3.googleusercontent.com/photo");
        assertThat(place.getThumbnailSource()).isEqualTo(ThumbnailSource.GOOGLE_PLACES);
        assertThat(place.getThumbnailAttribution()).isEqualTo("Contributor");
        verify(placeRepository).save(place);
    }

    @Test
    void fetchAndUpdate_bothFail_marksAsNone() {
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(wikimediaClient.fetchImage("경복궁")).thenReturn(Optional.empty());
        when(googlePlacesClient.fetchImage("경복궁")).thenReturn(Optional.empty());

        thumbnailFetcher.fetchAndUpdate(placeId);

        assertThat(place.getThumbnailUrl()).isNull();
        assertThat(place.getThumbnailSource()).isEqualTo(ThumbnailSource.NONE);
        verify(placeRepository).save(place);
    }

    @Test
    void fetchAndUpdate_alreadySearched_skips() {
        place.updateThumbnail("https://existing.com/img.jpg", ThumbnailSource.WIKIMEDIA);
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));

        thumbnailFetcher.fetchAndUpdate(placeId);

        verify(wikimediaClient, never()).fetchImage(any());
        verify(googlePlacesClient, never()).fetchImage(any());
        verify(placeRepository, never()).save(any());
    }

    @Test
    void fetchAndUpdate_markedAsNone_skips() {
        place.updateThumbnail(null, ThumbnailSource.NONE);
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));

        thumbnailFetcher.fetchAndUpdate(placeId);

        verify(wikimediaClient, never()).fetchImage(any());
        verify(googlePlacesClient, never()).fetchImage(any());
        verify(placeRepository, never()).save(any());
    }

    @Test
    void fetchAndUpdate_placeNotFound_doesNothing() {
        when(placeRepository.findById(placeId)).thenReturn(Optional.empty());

        thumbnailFetcher.fetchAndUpdate(placeId);

        verify(wikimediaClient, never()).fetchImage(any());
        verify(placeRepository, never()).save(any());
    }
}
