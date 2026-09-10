package roundtrip.loadtest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AiConcurrencyLoadAssetsTest {

    @Test
    void k6ScriptUsesClosedWorkloadAndDoesNotCallRealProvider() throws IOException {
        String script = read("observability/loadtest/k6/ai-concurrency.js");
        assertThat(script).contains("VUS");
        assertThat(script).contains("internal/ai-loadtest/call");
        assertThat(script).doesNotContain("api.featherless.ai");
    }

    @Test
    void reportAnswersSaturationQuestionsWithMeasuredTable() throws IOException {
        String report = read("docs/performance/ai-load-test.md");
        assertThat(report).contains("포화는 어느 동시성부터인가");
        assertThat(report).contains("처리량은 어디서 멈추는가");
        assertThat(report).contains("대기 지연은 언제 급증하는가");
        assertThat(report).contains("세마포어가 Provider를 보호하는가");
        assertThat(report).contains("사용자 체감 지연은 어디서 나빠지는가");
        assertThat(report).contains("| 동시성 | 요청 수 | RPS | 성공률 | 오류율 | p50 | p95 | p99 | Provider p95 | 대기 p95 | 획득 타임아웃 |");
        assertThat(report).contains("실제 외부 API는 호출하지 않았다");
        assertThat(report).doesNotContain("api.featherless.ai");
    }

    private static String read(String relativePath) throws IOException {
        Path file = repoRoot().resolve(relativePath);
        assertThat(file).exists();
        return Files.readString(file);
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docs/performance/ai-load-test.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root with load-test assets was not found");
    }
}
