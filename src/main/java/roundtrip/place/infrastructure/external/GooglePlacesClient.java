package roundtrip.place.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import roundtrip.common.observability.AiProviderMetrics;
import roundtrip.place.domain.entity.ThumbnailImage;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GooglePlacesClient {

    private static final int MAX_WIDTH = 800;

    private final RestClient restClient;
    private final AiProviderMetrics metrics;

    public GooglePlacesClient(GooglePlacesProperties properties, AiProviderMetrics metrics) {
        this.metrics = metrics;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Goog-Api-Key", properties.apiKey())
                .build();
    }

    /**
     * 장소명으로 Google Places를 검색하고 첫 번째 결과의 사진 URL을 반환한다.
     */
    public Optional<ThumbnailImage> fetchImage(String placeName) {
        log.debug("Fetching Google Places image");
        try {
            return metrics.recordCall("google", "place_search", () -> fetchImageInternal(placeName));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<ThumbnailImage> fetchImageInternal(String placeName) {
        var searchBody = new TextSearchRequest(placeName);
        TextSearchResponse searchResponse = restClient.post()
                .uri("/v1/places:searchText")
                .header("X-Goog-FieldMask", "places.photos")
                .body(searchBody)
                .retrieve()
                .body(TextSearchResponse.class);

        if (searchResponse == null || searchResponse.places() == null || searchResponse.places().isEmpty()) {
            return Optional.empty();
        }

        PlaceResult firstPlace = searchResponse.places().get(0);
        if (firstPlace.photos() == null || firstPlace.photos().isEmpty()) {
            return Optional.empty();
        }

        String photoName = firstPlace.photos().get(0).name();
        if (photoName == null || photoName.isBlank()) {
            return Optional.empty();
        }

        PhotoMediaResponse media = restClient.get()
                .uri("/v1/" + photoName + "/media?maxWidthPx=" + MAX_WIDTH
                        + "&skipHttpRedirect=true")
                .retrieve()
                .body(PhotoMediaResponse.class);

        if (media == null || media.photoUri() == null || media.photoUri().isBlank()) {
            return Optional.empty();
        }

        String attribution = firstPlace.photos().get(0).authorAttributions() == null
                ? null
                : firstPlace.photos().get(0).authorAttributions().stream()
                        .map(AuthorAttribution::displayName)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .reduce((left, right) -> left + ", " + right)
                        .orElse(null);
        String attributionUrl = firstPlace.photos().get(0).authorAttributions() == null
                ? null
                : firstPlace.photos().get(0).authorAttributions().stream()
                        .map(AuthorAttribution::uri)
                        .filter(uri -> uri != null && !uri.isBlank())
                        .findFirst()
                        .orElse(null);
        return Optional.of(new ThumbnailImage(
                media.photoUri(), attribution, null, null, attributionUrl));
    }

    record TextSearchRequest(String textQuery) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextSearchResponse(List<PlaceResult> places) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlaceResult(List<Photo> photos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Photo(String name, List<AuthorAttribution> authorAttributions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthorAttribution(String displayName, String uri, String photoUri) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhotoMediaResponse(String name, String photoUri) {}
}
