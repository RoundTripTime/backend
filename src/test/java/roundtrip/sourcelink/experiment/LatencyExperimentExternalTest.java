package roundtrip.sourcelink.experiment;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiClient;
import roundtrip.sourcelink.infrastructure.external.PlaceParseResult;
import roundtrip.sourcelink.infrastructure.external.SupadataClient;
import roundtrip.sourcelink.infrastructure.external.SupadataExtractResponse;
import roundtrip.sourcelink.infrastructure.external.SupadataExtractResultResponse;
import roundtrip.sourcelink.infrastructure.external.SupadataMetadataResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 실제 외부 API(Supadata, Gemini)를 호출하여 파이프라인 단계별 레이턴시를 측정.
 *
 * 실행: ./gradlew experimentTest --tests "*LatencyExperiment*"
 * 결과: build/experiment-results/latency.csv
 */
@Tag("experiment")
@Tag("external")
class LatencyExperimentExternalTest {

    private static final List<VideoCase> VIDEOS = List.of(
            new VideoCase("12places", "https://www.youtube.com/shorts/8stXEkFjqts"),
            new VideoCase("7places",  "https://www.youtube.com/shorts/NlQqFDp1f_I"),
            new VideoCase("video_a",  "https://www.youtube.com/shorts/mwjEAv4vkHQ"),
            new VideoCase("video_b",  "https://www.youtube.com/shorts/c2phgprBBjI"),
            new VideoCase("video_c",  "https://www.youtube.com/shorts/YR0R59tHzZI"),
            new VideoCase("video_d",  "https://www.youtube.com/shorts/Vo4IgTXelHw"),
            new VideoCase("video_e",  "https://www.youtube.com/shorts/tZZXmologcE")
    );
    private static final int TRIALS = 2;

    private record VideoCase(String label, String url) {}

    @Test
    void measureStageLatencies() throws Exception {
        SupadataClient supadata = ExperimentEnv.supadataClient();
        FeatherlessAiClient gemini = ExperimentEnv.featherlessAiClient();

        Path csv = ExperimentEnv.resultsDir().resolve("latency-qwen.csv");
        writeLine(csv, "video,trial,stage,duration_ms,places_found,extra", false);

        for (VideoCase video : VIDEOS) {
            for (int trial = 1; trial <= TRIALS; trial++) {
                System.out.printf("%n=== %s trial %d/%d ===%n", video.label, trial, TRIALS);
                runOnePipeline(supadata, gemini, video, trial, csv);
                // 쿼터/레이트 리밋 완화용 간격
                Thread.sleep(2_000);
            }
        }

        System.out.println("\n[Latency] CSV → " + csv.toAbsolutePath());
        printSummary(csv);
    }

    private void runOnePipeline(SupadataClient supadata, FeatherlessAiClient gemini,
                                 VideoCase video, int trial, Path csv) throws IOException {
        long t0 = System.nanoTime();
        SupadataMetadataResponse metadata = supadata.fetchMetadata(video.url);
        long tMeta = elapsedMs(t0);
        writeLine(csv, row(video, trial, "supadata_metadata", tMeta, 0, ""), true);
        System.out.printf("  [meta] %d ms%n", tMeta);

        String metadataContent = buildMetadataContent(metadata);

        long t1 = System.nanoTime();
        List<PlaceParseResult> phase1 = gemini.parsePlaces(metadataContent);
        long tPhase1 = elapsedMs(t1);
        writeLine(csv, row(video, trial, "qwen_phase1", tPhase1, phase1.size(), ""), true);
        System.out.printf("  [phase1 qwen] %d ms, %d places%n", tPhase1, phase1.size());

        long tExtractSubmit = 0;
        long tExtractPoll = 0;
        int pollCount = 0;
        long tPhase2 = 0;
        int phase2Size = -1;

        if (phase1.isEmpty()) {
            String prompt = "이 영상에서 언급되는 실제 장소를 찾아주세요.\n제목: "
                    + nullSafe(metadata.title()) + "\n설명: " + nullSafe(metadata.description());

            long t2 = System.nanoTime();
            SupadataExtractResponse submit = supadata.submitExtract(video.url, prompt);
            tExtractSubmit = elapsedMs(t2);
            writeLine(csv, row(video, trial, "supadata_extract_submit", tExtractSubmit, 0, ""), true);
            System.out.printf("  [extract submit] %d ms, jobId=%s%n", tExtractSubmit, submit.jobId());

            long t3 = System.nanoTime();
            SupadataExtractResultResponse extractResult = null;
            for (int i = 0; i < 10; i++) {
                pollCount++;
                extractResult = supadata.getExtractResult(submit.jobId());
                if ("completed".equals(extractResult.status()) || "failed".equals(extractResult.status())) {
                    break;
                }
                try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            tExtractPoll = elapsedMs(t3);
            writeLine(csv, row(video, trial, "supadata_extract_poll", tExtractPoll, 0, "polls=" + pollCount), true);
            System.out.printf("  [extract poll]  %d ms over %d polls%n", tExtractPoll, pollCount);

            if (extractResult != null && "completed".equals(extractResult.status())) {
                String fullContent = "제목: " + nullSafe(metadata.title())
                        + "\n설명: " + nullSafe(metadata.description())
                        + "\n추출 내용: " + (extractResult.data() != null ? extractResult.data().toString() : "");
                long t4 = System.nanoTime();
                List<PlaceParseResult> phase2 = gemini.parsePlaces(fullContent);
                tPhase2 = elapsedMs(t4);
                phase2Size = phase2.size();
                writeLine(csv, row(video, trial, "qwen_phase2", tPhase2, phase2Size, ""), true);
                System.out.printf("  [phase2 qwen] %d ms, %d places%n", tPhase2, phase2Size);
            }
        }

        long total = tMeta + tPhase1 + tExtractSubmit + tExtractPoll + tPhase2;
        int finalCount = phase2Size >= 0 ? phase2Size : phase1.size();
        String winningPhase = phase2Size >= 0 ? "phase2" : "phase1";
        writeLine(csv, row(video, trial, "TOTAL", total, finalCount, "winning=" + winningPhase), true);
        System.out.printf("  [TOTAL] %d ms (%d places, %s)%n", total, finalCount, winningPhase);
    }

    private String buildMetadataContent(SupadataMetadataResponse metadata) {
        return "제목: " + nullSafe(metadata.title()) +
               "\n설명: " + nullSafe(metadata.description()) +
               "\n태그: " + (metadata.tags() != null ? String.join(", ", metadata.tags()) : "");
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String row(VideoCase v, int trial, String stage, long ms, int places, String extra) {
        return String.format(Locale.ROOT, "%s,%d,%s,%d,%d,%s", v.label, trial, stage, ms, places, extra);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private static void writeLine(Path path, String line, boolean append) throws IOException {
        StandardOpenOption[] opts = append
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
        Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8), opts);
    }

    private static void printSummary(Path csv) throws IOException {
        System.out.println("\n--- Stage latency summary (ms) ---");
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        // group by stage
        java.util.Map<String, List<Long>> byStage = new java.util.LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i).split(",");
            byStage.computeIfAbsent(cols[2], k -> new ArrayList<>()).add(Long.parseLong(cols[3]));
        }
        System.out.printf("%-26s | %5s | %6s | %6s | %6s | %6s%n",
                "stage", "n", "min", "p50", "p95", "max");
        System.out.println("-".repeat(70));
        byStage.forEach((stage, vals) -> {
            Collections.sort(vals);
            System.out.printf("%-26s | %5d | %6d | %6d | %6d | %6d%n",
                    stage, vals.size(), vals.get(0),
                    percentile(vals, 50), percentile(vals, 95),
                    vals.get(vals.size() - 1));
        });
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(sorted.size() * p / 100.0) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
