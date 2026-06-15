package roundtrip.sourcelink.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.domain.repository.PlaceCandidateRepository;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.extract.domain.repository.ExtractionJobRepository;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.entity.PlaceCategory;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.sourcelink.domain.repository.SourceLinkRepository;
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiClient;
import roundtrip.sourcelink.infrastructure.external.KakaoLocalClient;
import roundtrip.sourcelink.infrastructure.external.KakaoLocalDocument;
import roundtrip.place.application.ThumbnailFetcher;
import roundtrip.sourcelink.infrastructure.external.PlaceParseResult;
import roundtrip.sourcelink.infrastructure.external.SupadataClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtractionPipelineServiceTest {

    @Mock ExtractionJobRepository extractionJobRepository;
    @Mock SourceLinkRepository sourceLinkRepository;
    @Mock PlaceCandidateRepository placeCandidateRepository;
    @Mock PlaceRepository placeRepository;
    @Mock SupadataClient supadataClient;
    @Mock FeatherlessAiClient featherlessAiClient;
    @Mock KakaoLocalClient kakaoLocalClient;
    @Mock ObjectMapper objectMapper;
    @Mock ThumbnailFetcher thumbnailFetcher;

    @InjectMocks ExtractionPipelineService service;

    @Test
    void normalizePlaces_kakaoMatch_linksSavedPlaceToCandidate() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        ExtractionJob job = ExtractionJob.create(UUID.randomUUID());
        ReflectionTestUtils.setField(job, "id", jobId);

        KakaoLocalDocument document = new KakaoLocalDocument(
                "12345678", "시부야 스크램블 교차로", "AT4",
                "139.700440", "35.659513", "", "");
        when(kakaoLocalClient.searchByKeyword("시부야 스크램블 교차로"))
                .thenReturn(List.of(document));
        when(placeRepository.findByKakaoPlaceId("12345678")).thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            ReflectionTestUtils.setField(place, "id", placeId);
            return place;
        });
        when(objectMapper.writeValueAsString(document)).thenReturn("{\"id\":\"12345678\"}");
        when(placeCandidateRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PlaceCandidate> candidates = service.normalizePlaces(
                List.of(new PlaceParseResult(
                        "시부야 스크램블 교차로", "관광명소", 0.92, "영상 설명")),
                job);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.getPlaceId()).isEqualTo(placeId);
            assertThat(candidate.getProviderMatchJson()).contains("12345678");
        });
        verify(thumbnailFetcher).fetchAndUpdate(placeId);
    }

    @Test
    void normalizePlaces_kakaoMatch_reusesExistingPlace() throws Exception {
        UUID jobId = UUID.randomUUID();
        ExtractionJob job = ExtractionJob.create(UUID.randomUUID());
        ReflectionTestUtils.setField(job, "id", jobId);
        Place existingPlace = Place.create(
                "기존 장소", null, null, PlaceCategory.CAFE, "KR", "kakao-existing", null);
        UUID placeId = UUID.randomUUID();
        ReflectionTestUtils.setField(existingPlace, "id", placeId);

        KakaoLocalDocument document = new KakaoLocalDocument(
                "kakao-existing", "기존 장소", "CE7", "127.0", "37.0", "", "");
        when(kakaoLocalClient.searchByKeyword("기존 장소")).thenReturn(List.of(document));
        when(placeRepository.findByKakaoPlaceId("kakao-existing")).thenReturn(Optional.of(existingPlace));
        when(objectMapper.writeValueAsString(document)).thenReturn("{}");
        when(placeCandidateRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PlaceCandidate> candidates = service.normalizePlaces(
                List.of(new PlaceParseResult("기존 장소", "카페", 0.9, null)), job);

        assertThat(candidates).singleElement()
                .extracting(PlaceCandidate::getPlaceId)
                .isEqualTo(placeId);
        verify(thumbnailFetcher).fetchAndUpdate(placeId);
    }
}
