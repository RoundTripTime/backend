package roundtrip.sourcelink.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiClient {

    private static final String PROMPT_TEMPLATE =
            "다음은 소셜 미디어 영상의 정보입니다. 이 영상에서 언급되는 실제 장소(식당, 카페, 관광명소, 숙소 등)의 목록을 JSON 형식으로 추출해주세요. " +
            "각 장소에 대해 name(장소명), category(attraction/restaurant/cafe/accommodation/nature/etc 중 하나), confidence(0.0~1.0), evidence(근거 텍스트) 를 포함하세요. " +
            "장소를 특정할 수 없거나 정보가 부족하면 빈 배열을 반환하세요.\n" +
            "응답 형식: {\"places\": [{\"name\": \"장소명\", \"category\": \"카테고리\", \"confidence\": 0.9, \"evidence\": \"근거\"}]}\n\n" +
            "영상 정보:\n{content}";

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    public List<GeminiPlaceParseResult> parsePlaces(String content) {
        String prompt = PROMPT_TEMPLATE.replace("{content}", content);
        var requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        try {
            GeminiResponse response = restClient.post()
                    .uri("/models/{model}:generateContent?key={key}", properties.model(), properties.apiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                return Collections.emptyList();
            }

            String text = response.candidates().get(0).content().parts().get(0).text();
            return parseJsonResponse(text);
        } catch (Exception e) {
            log.warn("Gemini API call failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<GeminiPlaceParseResult> parseJsonResponse(String text) {
        try {
            // Extract JSON from markdown code block if present
            String json = text.trim();
            if (json.contains("```json")) {
                int start = json.indexOf("```json") + 7;
                int end = json.lastIndexOf("```");
                if (end > start) {
                    json = json.substring(start, end).trim();
                }
            } else if (json.contains("```")) {
                int start = json.indexOf("```") + 3;
                int end = json.lastIndexOf("```");
                if (end > start) {
                    json = json.substring(start, end).trim();
                }
            }

            var root = objectMapper.readTree(json);
            var placesNode = root.get("places");
            if (placesNode == null || !placesNode.isArray()) {
                return Collections.emptyList();
            }

            return objectMapper.readerForListOf(GeminiPlaceParseResult.class)
                    .readValue(placesNode);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}
}
