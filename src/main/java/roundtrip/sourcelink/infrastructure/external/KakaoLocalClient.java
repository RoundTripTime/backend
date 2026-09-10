package roundtrip.sourcelink.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import roundtrip.common.observability.AiProviderMetrics;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class KakaoLocalClient {

    private final RestClient restClient;
    private final AiProviderMetrics metrics;

    public KakaoLocalClient(KakaoLocalProperties properties, AiProviderMetrics metrics) {
        this.metrics = metrics;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "KakaoAK " + properties.apiKey())
                .build();
    }

    public List<KakaoLocalDocument> searchByKeyword(String name) {
        log.debug("Searching Kakao Local");
        try {
            return metrics.recordCall("kakao", "place_search", () -> {
                KakaoSearchResponse response = restClient.get()
                        .uri("/v2/local/search/keyword.json?query={query}&page=1&size=5", name)
                        .retrieve()
                        .body(KakaoSearchResponse.class);

                if (response == null || response.documents() == null) {
                    return Collections.emptyList();
                }
                return response.documents();
            });
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoSearchResponse(List<KakaoLocalDocument> documents) {}
}
