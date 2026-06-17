package roundtrip.place.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestClient;
import roundtrip.place.domain.entity.ThumbnailImage;

import java.util.List;
import java.util.Optional;

/**
 * 네이버 이미지 검색 API로 장소 이미지를 가져온다.
 * Wikimedia / Google Places가 모두 실패했을 때의 최후 폴백이다.
 *
 * 검색엔진 결과라 이미지 품질·정합성이 보장되지 않으므로, 네이버가 캐싱한
 * thumbnail URL을 우선 사용해 렌더링 실패(원본 핫링크 차단·404) 가능성을 줄인다.
 */
@Slf4j
@Component
public class NaverImageSearchClient {

    private static final int DISPLAY = 1;

    private final RestClient restClient;

    public NaverImageSearchClient(NaverImageSearchProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Naver-Client-Id", properties.clientId())
                .defaultHeader("X-Naver-Client-Secret", properties.clientSecret())
                .build();
    }

    public Optional<ThumbnailImage> fetchImage(String placeName) {
        log.debug("Fetching Naver image for place={}", placeName);
        try {
            NaverImageResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/image")
                            .queryParam("query", placeName)
                            .queryParam("display", DISPLAY)
                            .queryParam("sort", "sim")
                            .queryParam("filter", "large")
                            .build())
                    .retrieve()
                    .body(NaverImageResponse.class);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                return Optional.empty();
            }

            NaverImageItem item = response.items().get(0);
            // 네이버가 캐싱한 thumbnail을 우선 사용하고, 없으면 원본 link로 폴백한다.
            String url = hasText(item.thumbnail()) ? item.thumbnail() : item.link();
            if (!hasText(url)) {
                return Optional.empty();
            }

            String attribution = hasText(item.title())
                    ? HtmlUtils.htmlUnescape(item.title().replaceAll("<[^>]*>", "")).trim()
                    : null;
            return Optional.of(new ThumbnailImage(url, attribution, null, null, item.link()));

        } catch (Exception e) {
            log.warn("Naver image search failed for place={}: {}", placeName, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NaverImageResponse(List<NaverImageItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NaverImageItem(String title, String link, String thumbnail) {}
}
