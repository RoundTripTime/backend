package roundtrip.sourcelink.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import roundtrip.common.infrastructure.FeatherlessAiRateLimiter;
import roundtrip.common.infrastructure.FeatherlessAiResponseSanitizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class FeatherlessAiClient {

    private static final String SYSTEM_PROMPT =
            "당신은 소셜 미디어 영상 정보에서 실제 장소를 추출하는 전문가입니다. " +
            "응답은 반드시 JSON만 반환하세요. 마크다운이나 설명 텍스트를 포함하지 마세요. " +
            "/no_think";

    private static final String USER_PROMPT_TEMPLATE =
            "다음은 소셜 미디어 영상의 정보입니다. 이 영상에서 언급되는 실제 장소(식당, 카페, 관광명소, 숙소, 자연 등)의 목록을 JSON 형식으로 추출해주세요.\n\n" +
            "규칙:\n" +
            "1. 각 장소에 대해 name(장소명), category, confidence(0.0~1.0), evidence(근거 텍스트)를 포함하세요.\n" +
            "2. category는 반드시 다음 중 하나: attraction, restaurant, cafe, accommodation, nature, etc\n" +
            "3. name은 지도 검색에 사용되므로 정확한 공식 장소명을 사용하세요. 예: \"스타벅스 강남역점\", \"교토 금각사\"\n" +
            "4. 일반적인 지역명(\"도쿄\", \"강남\")은 제외하고, 특정 가게/명소/건물만 추출하세요.\n" +
            "5. 장소를 특정할 수 없거나 정보가 부족하면 빈 배열을 반환하세요.\n\n" +
            "응답 형식: {\"places\": [{\"name\": \"장소명\", \"category\": \"카테고리\", \"confidence\": 0.9, \"evidence\": \"근거\"}]}\n\n" +
            "영상 정보:\n{content}";

    private final RestClient restClient;
    private final FeatherlessAiProperties properties;
    private final ObjectMapper objectMapper;
    private final FeatherlessAiRateLimiter rateLimiter;

    public FeatherlessAiClient(FeatherlessAiProperties properties, ObjectMapper objectMapper,
                                FeatherlessAiRateLimiter rateLimiter) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.featherless.ai/v1")
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public List<PlaceParseResult> parsePlaces(String content) {
        String userPrompt = USER_PROMPT_TEMPLATE.replace("{content}", content);
        var requestBody = Map.of(
                "model", properties.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.1,
                "max_tokens", 4096
        );

        if (!rateLimiter.tryAcquire(60, TimeUnit.SECONDS)) {
            log.warn("FeatherlessAI rate limit: could not acquire permit for place parsing");
            return Collections.emptyList();
        }
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return Collections.emptyList();
            }

            String text = response.choices().get(0).message().content();
            return parseJsonResponse(text);
        } catch (Exception e) {
            log.warn("FeatherlessAI API call failed: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            rateLimiter.release();
        }
    }

    List<PlaceParseResult> parseJsonResponse(String text) {
        try {
            String sanitized = FeatherlessAiResponseSanitizer.stripThinking(text);
            for (String json : findJsonObjects(sanitized)) {
                JsonNode root;
                try {
                    root = objectMapper.readTree(json);
                } catch (Exception ignored) {
                    continue;
                }

                var placesNode = root.get("places");
                if (placesNode != null && placesNode.isArray()) {
                    return objectMapper.readerForListOf(PlaceParseResult.class)
                            .readValue(placesNode);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse FeatherlessAI JSON response: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> findJsonObjects(String text) {
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (current == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }

        return objects;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {}
}
