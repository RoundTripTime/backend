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
        when(wikimediaClient.fetchImageUrl("경복궁"))
                .thenReturn(Optional.of("https://upload.wikimedia.org/thumb/gyeongbokgung.jpg"));

        thumbnailFetcher.fetchAndUpdate(placeId);

        assertThat(place.getThumbnailUrl()).isEqualTo("https://upload.wikimedia.org/thumb/gyeongbokgung.jpg");
        assertThat(place.getThumbnailSource()).isEqualTo(ThumbnailSource.WIKIMEDIA);
        verify(placeRepository).save(place);
        verify(googlePlacesClient, never()).fetchImageUrl(any());
    }

    @Test
    void fetchAndUpdate_wikimediaFails_fallsBackToGooglePlaces() {
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(wikimediaClient.fetchImageUrl("경복궁")).thenReturn(Optional.empty());
        when(googlePlacesClient.fetchImageUrl("경복궁"))
                .thenReturn(Optional.of("https://places.googleapis.com/v1/photo/media?key=abc"));

        thumbnailFetcher.fetchAndUpdate(placeId);

        assertThat(place.getThumbnailUrl()).isEqualTo("https://places.googleapis.com/v1/photo/media?key=abc");
        assertThat(place.getThumbnailSource()).isEqualTo(ThumbnailSource.GOOGLE_PLACES);
        verify(placeRepository).save(place);
    }

    @Test
    void fetchAndUpdate_bothFail_marksAsNone() {
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));
        when(wikimediaClient.fetchImageUrl("경복궁")).thenReturn(Optional.empty());
        when(googlePlacesClient.fetchImageUrl("경복궁")).thenReturn(Optional.empty());

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

        verify(wikimediaClient, never()).fetchImageUrl(any());
        verify(googlePlacesClient, never()).fetchImageUrl(any());
        verify(placeRepository, never()).save(any());
    }

    @Test
    void fetchAndUpdate_markedAsNone_skips() {
        place.updateThumbnail(null, ThumbnailSource.NONE);
        when(placeRepository.findById(placeId)).thenReturn(Optional.of(place));

        thumbnailFetcher.fetchAndUpdate(placeId);

        verify(wikimediaClient, never()).fetchImageUrl(any());
        verify(googlePlacesClient, never()).fetchImageUrl(any());
        verify(placeRepository, never()).save(any());
    }

    @Test
    void fetchAndUpdate_placeNotFound_doesNothing() {
        when(placeRepository.findById(placeId)).thenReturn(Optional.empty());

        thumbnailFetcher.fetchAndUpdate(placeId);

        verify(wikimediaClient, never()).fetchImageUrl(any());
        verify(placeRepository, never()).save(any());
    }
}
