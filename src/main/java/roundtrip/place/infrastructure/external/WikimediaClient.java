package roundtrip.place.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestClient;
import roundtrip.place.domain.entity.ThumbnailImage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;

@Slf4j
@Component
public class WikimediaClient {

    private static final String BASE_URL = "https://commons.wikimedia.org/w/api.php";
    private static final int THUMB_WIDTH = 800;

    private final RestClient restClient;

    public WikimediaClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent",
                        "RoundtripThumbnailBot/1.0 (https://github.com/RoundTripTime/backend)")
                .build();
    }

    /**
     * 장소명으로 Wikimedia Commons에서 이미지 URL을 검색한다.
     * 검색 결과 중 첫 번째 이미지의 썸네일 URL을 반환한다.
     */
    public Optional<ThumbnailImage> fetchImage(String placeName) {
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
                            .queryParam("iiprop", "url|extmetadata")
                            .queryParam("iiurlwidth", String.valueOf(THUMB_WIDTH))
                            .queryParam("iiextmetadatafilter", "Artist|LicenseShortName|LicenseUrl|Credit")
                            .build())
                    .retrieve()
                    .body(WikimediaResponse.class);

            if (response == null || response.query() == null || response.query().pages() == null) {
                return Optional.empty();
            }

            return response.query().pages().values().stream()
                    .filter(page -> page.imageinfo() != null && !page.imageinfo().isEmpty())
                    .filter(page -> isValidImage(page.title()))
                    .sorted(Comparator.comparingInt(
                            page -> page.index() != null ? page.index() : Integer.MAX_VALUE))
                    .map(page -> toThumbnailImage(page.imageinfo().get(0)))
                    .filter(image -> image.url() != null && !image.url().isBlank())
                    .findFirst();

        } catch (Exception e) {
            log.warn("Wikimedia API call failed for place={}: {}", placeName, e.getMessage());
            return Optional.empty();
        }
    }

    private ThumbnailImage toThumbnailImage(ImageInfo info) {
        Map<String, MetadataValue> metadata = info.extmetadata();
        return new ThumbnailImage(
                info.thumburl(),
                metadataValue(metadata, "Artist"),
                metadataValue(metadata, "LicenseShortName"),
                metadataValue(metadata, "LicenseUrl"),
                info.descriptionurl()
        );
    }

    private String metadataValue(Map<String, MetadataValue> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) return null;
        String value = metadata.get(key).value();
        if (value == null) return null;
        return HtmlUtils.htmlUnescape(value.replaceAll("<[^>]*>", "")).trim();
    }

    private boolean isValidImage(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp");
    }

    public record WikimediaResponse(Query query) {}

    public record Query(Map<String, Page> pages) {}

    public record Page(String title, Integer index, List<ImageInfo> imageinfo) {}

    public record ImageInfo(String url, String thumburl, String descriptionurl,
                            Map<String, MetadataValue> extmetadata) {}

    public record MetadataValue(String value) {}
}
