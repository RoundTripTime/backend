package roundtrip.sourcelink.experiment;

import org.mockito.Mockito;
import roundtrip.common.infrastructure.FeatherlessAiRateLimiter;
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiClient;
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiProperties;
import roundtrip.sourcelink.infrastructure.external.SupadataClient;
import roundtrip.sourcelink.infrastructure.external.SupadataProperties;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ExperimentEnv {

    private static Map<String, String> CACHE;

    private ExperimentEnv() {}

    static synchronized Map<String, String> load() {
        if (CACHE != null) return CACHE;
        Map<String, String> map = new HashMap<>();
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            try {
                for (String raw : Files.readAllLines(envFile)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    map.put(key, value);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read .env", e);
            }
        }
        for (var entry : System.getenv().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        CACHE = Map.copyOf(map);
        return CACHE;
    }

    static String require(String key) {
        String v = load().get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing env var: " + key + " (set in .env)");
        }
        return v;
    }

    static SupadataClient supadataClient() {
        return new SupadataClient(new SupadataProperties(
                require("SUPADATA_API_KEY"),
                "https://api.supadata.ai/v1"
        ));
    }

    static FeatherlessAiClient featherlessAiClient() {
        FeatherlessAiRateLimiter noopLimiter = Mockito.mock(FeatherlessAiRateLimiter.class);
        Mockito.when(noopLimiter.tryAcquire(Mockito.anyLong(), Mockito.any(TimeUnit.class)))
                .thenReturn(true);
        return new FeatherlessAiClient(
                new FeatherlessAiProperties(require("FEATHERLESSAI_API_KEY"), "Qwen/Qwen3-235B-A22B"),
                JsonMapper.builder().build(),
                noopLimiter
        );
    }

    static Path resultsDir() {
        Path dir = Paths.get("build", "experiment-results");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create results dir", e);
        }
        return dir;
    }
}
