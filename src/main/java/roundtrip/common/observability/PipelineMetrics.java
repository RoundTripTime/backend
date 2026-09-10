package roundtrip.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class PipelineMetrics {

    static final String EXTRACTION_DURATION = "extraction.pipeline.duration";
    static final String EXTRACTION_STAGE_DURATION = "extraction.pipeline.stage.duration";
    static final String PLANNING_DURATION = "planning.agent.duration";
    static final String PLANNING_REQUESTS = "planning.agent.requests";

    public static final String SUCCESS = "success";
    public static final String PARTIAL_SUCCESS = "partial_success";
    public static final String FAILURE = "failure";

    private final MeterRegistry registry;

    public PipelineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordExtraction(String result, Timer.Sample sample) {
        sample.stop(extractionTimer(result));
    }

    public void recordExtraction(String result, Duration duration) {
        extractionTimer(result).record(duration);
    }

    public <T> T timeStage(String stage, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return action.get();
        } finally {
            sample.stop(stageTimer(stage));
        }
    }

    public void timeStage(String stage, Runnable action) {
        timeStage(stage, () -> {
            action.run();
            return null;
        });
    }

    public void recordPlanning(String result, Timer.Sample sample) {
        sample.stop(planningTimer());
        Counter.builder(PLANNING_REQUESTS)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void recordPlanning(String result, Duration duration) {
        planningTimer().record(duration);
        Counter.builder(PLANNING_REQUESTS)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    private Timer extractionTimer(String result) {
        return Timer.builder(EXTRACTION_DURATION)
                .tag("result", result)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    private Timer stageTimer(String stage) {
        return Timer.builder(EXTRACTION_STAGE_DURATION)
                .tag("stage", stage)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    private Timer planningTimer() {
        return Timer.builder(PLANNING_DURATION)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
