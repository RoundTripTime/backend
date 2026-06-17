package roundtrip.place.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.entity.ThumbnailSource;
import roundtrip.place.domain.entity.ThumbnailImage;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.place.infrastructure.external.GooglePlacesClient;
import roundtrip.place.infrastructure.external.NaverImageSearchClient;
import roundtrip.place.infrastructure.external.WikimediaClient;

import java.util.Optional;
import java.util.UUID;

/**
 * 장소 썸네일을 외부 소스에서 가져오는 서비스.
 * 폴백 순서: Wikimedia Commons → Google Places Photos → Naver Image Search
 *
 * thumbnailSource 상태:
 * - null: 아직 검색하지 않음
 * - NONE: 검색했지만 이미지 없음 (재시도 안 함)
 * - WIKIMEDIA / GOOGLE_PLACES / NAVER_IMAGE: 해당 소스에서 가져옴
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailFetcher {

    private final WikimediaClient wikimediaClient;
    private final GooglePlacesClient googlePlacesClient;
    private final NaverImageSearchClient naverImageSearchClient;
    private final PlaceRepository placeRepository;

    public void fetchAndUpdate(UUID placeId) {
        placeRepository.findById(placeId).ifPresent(place -> {
            if (place.getThumbnailSource() != null) {
                log.debug("Place {} already searched (source={}), skipping", placeId, place.getThumbnailSource());
                return;
            }
            doFetch(place);
        });
    }

    private void doFetch(Place place) {
        String placeName = place.getCanonicalName();

        // 1차: Wikimedia Commons
        Optional<ThumbnailImage> wikimediaImage = wikimediaClient.fetchImage(placeName);
        if (wikimediaImage.isPresent()) {
            place.applyThumbnail(wikimediaImage.get(), ThumbnailSource.WIKIMEDIA);
            placeRepository.save(place);
            log.info("Thumbnail set from Wikimedia for place={} ({})", place.getId(), placeName);
            return;
        }

        // 2차 폴백: Google Places
        Optional<ThumbnailImage> googleImage = googlePlacesClient.fetchImage(placeName);
        if (googleImage.isPresent()) {
            place.applyThumbnail(googleImage.get(), ThumbnailSource.GOOGLE_PLACES);
            placeRepository.save(place);
            log.info("Thumbnail set from Google Places for place={} ({})", place.getId(), placeName);
            return;
        }

        // 3차 폴백: Naver Image Search (검색엔진 결과, 최후 수단)
        Optional<ThumbnailImage> naverImage = naverImageSearchClient.fetchImage(placeName);
        if (naverImage.isPresent()) {
            place.applyThumbnail(naverImage.get(), ThumbnailSource.NAVER_IMAGE);
            placeRepository.save(place);
            log.info("Thumbnail set from Naver for place={} ({})", place.getId(), placeName);
            return;
        }

        // 모두 실패: NONE으로 마킹하여 재시도 방지
        place.updateThumbnail(null, ThumbnailSource.NONE);
        placeRepository.save(place);
        log.info("No thumbnail found for place={} ({}), marked as NONE", place.getId(), placeName);
    }
}
