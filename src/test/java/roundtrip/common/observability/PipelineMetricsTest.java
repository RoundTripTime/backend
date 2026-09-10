package roundtrip.common.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineMetricsTest {

    private SimpleMeterRegistry registry;
    private PipelineMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new PipelineMetrics(registry);
    }

    @Test
    void recordsExtractionOutcome() {
        metrics.recordExtraction(PipelineMetrics.SUCCESS, Duration.ofMillis(80));
        metrics.recordExtraction(PipelineMetrics.FAILURE, Duration.ofMillis(20));

        assertThat(registry.timer("extraction.pipeline.duration", "result", "success").count()).isEqualTo(1);
        assertThat(registry.timer("extraction.pipeline.duration", "result", "failure").count()).isEqualTo(1);
    }

    @Test
    void recordsStageLatencyAndReturnsSupplierValue() {
        AtomicInteger calls = new AtomicInteger();
        String value = metrics.timeStage("llm", () -> {
            calls.incrementAndGet();
            return "places";
        });

        assertThat(value).isEqualTo("places");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(registry.timer("extraction.pipeline.stage.duration", "stage", "llm").count()).isEqualTo(1);
    }

    @Test
    void recordsPlanningAgentDurationAndCount() {
        metrics.recordPlanning("success", Duration.ofMillis(30));
        metrics.recordPlanning("fallback", Duration.ofMillis(5));

        assertThat(registry.timer("planning.agent.duration").count()).isEqualTo(2);
        assertThat(registry.counter("planning.agent.requests", "result", "success").count()).isEqualTo(1);
        assertThat(registry.counter("planning.agent.requests", "result", "fallback").count()).isEqualTo(1);
    }
}
