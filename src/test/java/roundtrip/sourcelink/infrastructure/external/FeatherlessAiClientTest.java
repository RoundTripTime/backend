package roundtrip.sourcelink.infrastructure.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import roundtrip.common.infrastructure.FeatherlessAiRateLimiter;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeatherlessAiClientTest {

    private FeatherlessAiClient client;

    @BeforeEach
    void setUp() {
        client = new FeatherlessAiClient(
                new FeatherlessAiProperties("test-key", "test-model"),
                JsonMapper.builder().build(),
                mock(FeatherlessAiRateLimiter.class)
        );
    }

    @Test
    void parseJsonResponse_thinkBlockBeforeJson_parsesPlaces() {
        String response = """
                <think>
                장소 후보와 신뢰도를 계산한다.
                </think>
                {"places":[{"name":"경복궁","category":"attraction","confidence":0.95,"evidence":"영상 자막"}]}
                """;

        List<PlaceParseResult> places = client.parseJsonResponse(response);

        assertThat(places).containsExactly(
                new PlaceParseResult("경복궁", "attraction", 0.95, "영상 자막")
        );
    }

    @Test
    void parseJsonResponse_fencedJsonAndHtmlValue_parsesWithoutChangingValue() {
        String response = """
                분석 결과입니다.
                ```json
                {"places":[{"name":"카페 <strong>봄</strong>","category":"cafe","confidence":0.8,"evidence":"설명"}]}
                ```
                """;

        List<PlaceParseResult> places = client.parseJsonResponse(response);

        assertThat(places).extracting(PlaceParseResult::name)
                .containsExactly("카페 <strong>봄</strong>");
    }

    @Test
    void parseJsonResponse_bracesInsideJsonString_keepsObjectBoundary() {
        String response = """
                <think>{"temporary": true}</think>
                {"places":[{"name":"카페","category":"cafe","confidence":0.8,"evidence":"메뉴에 {\\"추천\\"} 표시"}]}
                """;

        List<PlaceParseResult> places = client.parseJsonResponse(response);

        assertThat(places).extracting(PlaceParseResult::evidence)
                .containsExactly("메뉴에 {\"추천\"} 표시");
    }

    @Test
    void parseJsonResponse_invalidResponse_returnsEmptyList() {
        assertThat(client.parseJsonResponse("<think>추론만 있음</think>")).isEmpty();
    }
}
