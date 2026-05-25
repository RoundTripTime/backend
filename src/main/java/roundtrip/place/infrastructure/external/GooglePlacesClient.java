package roundtrip.place.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GooglePlacesClient {

    private static final int MAX_WIDTH = 800;

    private final RestClient restClient;
    private final String apiKey;

    public GooglePlacesClient(GooglePlacesProperties properties) {
        this.apiKey = properties.apiKey();
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Goog-Api-Key", properties.apiKey())
                .build();
    }

    /**
     * 장소명으로 Google Places를 검색하고 첫 번째 결과의 사진 URL을 반환한다.
     */
    public Optional<String> fetchImageUrl(String placeName) {
        log.debug("Fetching Google Places image for place={}", placeName);
        try {
            // 1. Text Search로 장소 검색
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

            // 2. 첫 번째 장소의 첫 번째 사진 리소스명 추출
            PlaceResult firstPlace = searchResponse.places().get(0);
            if (firstPlace.photos() == null || firstPlace.photos().isEmpty()) {
                return Optional.empty();
            }

            String photoName = firstPlace.photos().get(0).name();
            if (photoName == null || photoName.isBlank()) {
                return Optional.empty();
            }

            // 3. Photo URI 구성 (Media endpoint)
            String photoUrl = String.format("%s/v1/%s/media?maxWidthPx=%d&key=%s",
                    "https://places.googleapis.com", photoName, MAX_WIDTH, apiKey);
            return Optional.of(photoUrl);

        } catch (Exception e) {
            log.warn("Google Places API call failed for place={}: {}", placeName, e.getMessage());
            return Optional.empty();
        }
    }

    record TextSearchRequest(String textQuery) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextSearchResponse(List<PlaceResult> places) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlaceResult(List<Photo> photos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Photo(String name) {}
}
