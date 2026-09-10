package roundtrip.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeOpsDocsTest {

    private static final List<String> DOC_PATHS = List.of(
            "docs/architecture/ai-runtime.md",
            "docs/operations/observability.md",
            "docs/operations/failure-handling.md",
            "docs/performance/ai-load-test.md",
            "docs/deployment/deployment-and-rollback.md"
    );

    @Test
    void readmeLinksRuntimeOpsDocs() throws IOException {
        String readme = read("README.md");
        for (String path : DOC_PATHS) {
            assertThat(readme).contains(path);
        }
    }

    @Test
    void docsMatchImplementedRuntimeAndDoNotClaimMissingPlatforms() throws IOException {
        String runtime = read("docs/architecture/ai-runtime.md");
        assertThat(runtime).contains("featherlessai:concurrency");
        assertThat(runtime).contains("PlanningAgentService");
        assertThat(runtime).contains("FeatherlessAiClient");
        assertThat(runtime).contains("place_extraction");
        assertThat(runtime).contains("planning_chat");
        assertThat(runtime).contains("10초");
        assertThat(runtime).contains("60초");

        String failure = read("docs/operations/failure-handling.md");
        assertThat(failure).contains("Retry-After");
        assertThat(failure).contains("max-wait-for-retry-after");
        assertThat(failure).contains("FeatherlessAiIsolation");
        assertThat(failure).contains("Supadata");
        assertThat(failure).contains("별개의 이슈로 처리한다");

        String observability = read("docs/operations/observability.md");
        assertThat(observability).contains("ai_provider_requests_total");
        assertThat(observability).contains("AIConcurrencySaturation");
        assertThat(observability).contains("/actuator/health/smoke");
        assertThat(observability).contains("performance/ai-load-test.md");

        String deploy = read("docs/deployment/deployment-and-rollback.md");
        assertThat(deploy).contains("scripts/deploy.sh");
        assertThat(deploy).contains("Verify");
        assertThat(deploy).contains("roundtrip-old.jar");
        assertThat(deploy).contains("/actuator/health/smoke");
        assertThat(deploy).contains("systemctl restart roundtrip");
        assertThat(deploy).doesNotContain("sk-");
        assertThat(deploy).doesNotContain("JWT_SECRET=");

        String load = read("docs/performance/ai-load-test.md");
        assertThat(load).contains("포화는 어느 동시성부터인가");
        assertThat(load).contains("대기 지연은 언제 급증하는가");

        assertThat(runtime).contains("Kubernetes나 서비스 메시를 쓰지 않는다");
        assertThat(observability).contains("OpenTelemetry 분산 추적을 도입하지 않았다");
        assertThat(deploy).contains("Kubernetes나 Helm으로 배포하지 않는다");
        for (String path : DOC_PATHS) {
            String body = read(path);
            assertThat(body).doesNotContain("sk-");
        }
    }

    private static String read(String relativePath) throws IOException {
        Path file = repoRoot().resolve(relativePath);
        assertThat(file).exists();
        return Files.readString(file);
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("README.md"))
                    && Files.isRegularFile(current.resolve("docs/architecture/ai-runtime.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root with ops docs was not found");
    }
}
