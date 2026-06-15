package roundtrip.common.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatherlessAiResponseSanitizerTest {

    @Test
    void stripThinking_removesThinkAndReasoningBlocks() {
        String content = """
                <think>내부 추론</think>
                <reasoning data-mode="deep">추가 추론</reasoning>
                사용자에게 보여줄 응답
                """;

        assertThat(FeatherlessAiResponseSanitizer.stripThinking(content))
                .isEqualTo("사용자에게 보여줄 응답");
    }

    @Test
    void stripThinking_preservesUnrelatedHtmlLikeText() {
        String content = "장소 설명에 <strong>강조</strong>가 포함됨";

        assertThat(FeatherlessAiResponseSanitizer.stripThinking(content))
                .isEqualTo(content);
    }
}
