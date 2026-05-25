package roundtrip.place.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class WikimediaClient {

    private static final String BASE_URL = "https://commons.wikimedia.org/w/api.php";
    private static final int THUMB_WIDTH = 800;

    private final RestClient restClient;

    public WikimediaClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * 장소명으로 Wikimedia Commons에서 이미지 URL을 검색한다.
     * 검색 결과 중 첫 번째 이미지의 썸네일 URL을 반환한다.
     */
    public Optional<String> fetchImageUrl(String placeName) {
        log.debug("Fetching Wikimedia image for place={}", placeName);
        try {
            WikimediaResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("action", "query")
                            .queryParam("format", "json")
                            .queryParam("generator", "search")
                            .queryParam("gsrsearch", placeName)
                            .queryParam("gsrnamespace", "6")
                            .queryParam("gsrlimit", "5")
                            .queryParam("prop", "imageinfo")
                            .queryParam("iiprop", "url")
                            .queryParam("iiurlwidth", String.valueOf(THUMB_WIDTH))
                            .build())
                    .retrieve()
                    .body(WikimediaResponse.class);

            if (response == null || response.query() == null || response.query().pages() == null) {
                return Optional.empty();
            }

            return response.query().pages().values().stream()
                    .filter(page -> page.imageinfo() != null && !page.imageinfo().isEmpty())
                    .filter(page -> isValidImage(page.title()))
                    .map(page -> page.imageinfo().get(0).thumburl())
                    .filter(url -> url != null && !url.isBlank())
                    .findFirst();

        } catch (Exception e) {
            log.warn("Wikimedia API call failed for place={}: {}", placeName, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isValidImage(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }

    public record WikimediaResponse(Query query) {}

    public record Query(Map<String, Page> pages) {}

    public record Page(String title, List<ImageInfo> imageinfo) {}

    public record ImageInfo(String url, String thumburl) {}
}
