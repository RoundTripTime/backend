package roundtrip.common.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AiOperationsObservabilityAssetsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ERROR_RESULTS =
            "timeout|rate_limited|client_error|server_error|circuit_open|unknown_error";

    @Test
    void dashboardExposesTrafficErrorLatencySemaphoreAndPipelinePanels() throws IOException {
        JsonNode dashboard = OBJECT_MAPPER.readTree(read("observability/grafana/dashboards/roundtrip-ai-operations.json"));

        assertThat(dashboard.path("title").asText()).isEqualTo("RoundTrip AI Operations");
        assertThat(panelTitles(dashboard)).containsExactly(
                "AI Provider Request Rate",
                "AI Provider Error Rate",
                "Provider p50",
                "Provider p95",
                "Provider p99",
                "AI Semaphore Used Permits",
                "AI Semaphore Available Permits",
                "AI Semaphore Wait p95",
                "Extraction Pipeline p95",
                "Planning Agent p95"
        );

        String exprs = allExprs(dashboard);
        assertThat(exprs).contains("ai_provider_requests_total");
        assertThat(exprs).contains("result=~\"" + ERROR_RESULTS + "\"");
        assertThat(exprs).contains("ai_provider_request_duration_seconds_bucket");
        assertThat(exprs).contains("ai_concurrency_used");
        assertThat(exprs).contains("ai_concurrency_available");
        assertThat(exprs).contains("ai_concurrency_wait_duration_seconds_bucket");
        assertThat(exprs).contains("extraction_pipeline_duration_seconds_bucket");
        assertThat(exprs).contains("planning_agent_duration_seconds_bucket");
        assertThat(exprs).doesNotContain("userId", "prompt", "requestId");
    }

    @Test
    void alertRulesCoverErrorRateLatencyAndSustainedSemaphoreSaturation() throws IOException {
        String rules = read("observability/prometheus/rules/ai-operations.yml");

        assertThat(alertNames(rules)).containsExactly(
                "AIProviderHighErrorRate",
                "AIProviderHighLatency",
                "AIConcurrencySaturation"
        );
        assertThat(rules).contains("result=~\"" + ERROR_RESULTS + "\"");
        assertThat(rules).contains("> 0.10");
        assertThat(rules).contains("> 60");
        assertThat(rules).contains("#99");
        assertThat(rules).containsPattern(
                "alert: AIConcurrencySaturation\\s+expr: min by \\(provider\\) \\(ai_concurrency_available\\) == 0\\s+for: 1m"
        );
        assertThat(rules).doesNotContain("userId", "prompt", "requestId");
    }

    private static List<String> panelTitles(JsonNode dashboard) {
        List<String> titles = new ArrayList<>();
        for (JsonNode panel : dashboard.path("panels")) {
            titles.add(panel.path("title").asText());
        }
        return titles;
    }

    private static String allExprs(JsonNode dashboard) {
        StringBuilder exprs = new StringBuilder();
        for (JsonNode panel : dashboard.path("panels")) {
            for (JsonNode target : panel.path("targets")) {
                exprs.append(target.path("expr").asText()).append('\n');
            }
        }
        return exprs.toString();
    }

    private static List<String> alertNames(String rules) {
        Matcher matcher = Pattern.compile("(?m)^\\s*- alert: (\\S+)").matcher(rules);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String read(String relativePath) throws IOException {
        Path file = repoRoot().resolve(relativePath);
        assertThat(file).exists();
        return Files.readString(file);
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("observability/grafana/dashboards/roundtrip-ai-operations.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root with observability assets was not found");
    }
}
