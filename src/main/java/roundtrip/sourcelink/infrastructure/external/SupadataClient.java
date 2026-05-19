package roundtrip.sourcelink.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SupadataClient {

    private final RestClient restClient;

    public SupadataClient(SupadataProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-api-key", properties.apiKey())
                .build();
    }

    public SupadataMetadataResponse fetchMetadata(String url) {
        log.debug("Fetching Supadata metadata for url={}", url);
        return restClient.get()
                .uri("/metadata?url={url}", url)
                .retrieve()
                .body(SupadataMetadataResponse.class);
    }

    public SupadataExtractResponse submitExtract(String url, String prompt) {
        log.debug("Submitting Supadata extract for url={}", url);
        var body = Map.of(
                "url", url,
                "prompt", prompt
        );
        return restClient.post()
                .uri("/extract")
                .body(body)
                .retrieve()
                .body(SupadataExtractResponse.class);
    }

    public SupadataExtractResultResponse getExtractResult(String jobId) {
        log.debug("Fetching Supadata extract result for jobId={}", jobId);
        return restClient.get()
                .uri("/extract/{jobId}", jobId)
                .retrieve()
                .body(SupadataExtractResultResponse.class);
    }
}
