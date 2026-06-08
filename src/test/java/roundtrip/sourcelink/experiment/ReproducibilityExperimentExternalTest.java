
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 같은 입력에 대해 Gemini를 N회 호출하여 LLM 비결정성을 측정.
 *
 *  - 메타데이터/자막은 한 번만 가져옴 (외부 호출 절약, 입력 고정)
 *  - Gemini만 N회 반복
 *  - 추출 장소 개수 분포 + 장소명 집합의 Jaccard 평균 측정
 *
 * 실행: ./gradlew experimentTest --tests "*ReproducibilityExperiment*"
 * 결과: build/experiment-results/reproducibility.csv
 */
@Tag("experiment")
@Tag("external")
class ReproducibilityExperimentExternalTest {

    private static final List<VideoCase> VIDEOS = List.of(
            new VideoCase("12places", "https://www.youtube.com/shorts/8stXEkFjqts"),
            new VideoCase("7places",  "https://www.youtube.com/shorts/NlQqFDp1f_I"),
            new VideoCase("video_a",  "https://www.youtube.com/shorts/mwjEAv4vkHQ"),
            new VideoCase("video_b",  "https://www.youtube.com/shorts/c2phgprBBjI"),
            new VideoCase("video_c",  "https://www.youtube.com/shorts/YR0R59tHzZI"),
            new VideoCase("video_d",  "https://www.youtube.com/shorts/Vo4IgTXelHw"),
            new VideoCase("video_e",  "https://www.youtube.com/shorts/tZZXmologcE")
    );
    private static final int TRIALS = 3;

    private record VideoCase(String label, String url) {}

    @Test
    void measureReproducibility() throws Exception {
        SupadataClient supadata = ExperimentEnv.supadataClient();
        FeatherlessAiClient gemini = ExperimentEnv.featherlessAiClient();

        Path csv = ExperimentEnv.resultsDir().resolve("reproducibility-qwen.csv");
        writeLine(csv, "video,trial,place_count,places", false);

        for (VideoCase video : VIDEOS) {
            System.out.printf("%n=== %s — preparing input ===%n", video.label);
            String input = prepareInput(supadata, video.url);
            System.out.printf("input length=%d chars%n", input.length());

            List<Set<String>> trials = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();

            for (int trial = 1; trial <= TRIALS; trial++) {
                List<PlaceParseResult> result = gemini.parsePlaces(input);
                Set<String> names = result.stream()
                        .map(r -> r.name() == null ? "" : r.name().trim().toLowerCase(Locale.ROOT))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(TreeSet::new));
                trials.add(names);
                counts.add(names.size());

                writeLine(csv, String.format(Locale.ROOT, "%s,%d,%d,%s",
                        video.label, trial, names.size(), String.join("|", names)), true);
                System.out.printf("  trial %d: %d places → %s%n",
                        trial, names.size(), names);
                Thread.sleep(1_500);
            }

            printVideoStats(video.label, counts, trials);
        }

        System.out.println("\n[Reproducibility] CSV → " + csv.toAbsolutePath());
    }

    private String prepareInput(SupadataClient supadata, String url) throws InterruptedException {
        SupadataMetadataResponse metadata = supadata.fetchMetadata(url);
        String metaContent = "제목: " + nullSafe(metadata.title()) +
                "\n설명: " + nullSafe(metadata.description()) +
                "\n태그: " + (metadata.tags() != null ? String.join(", ", metadata.tags()) : "");

        // 짧은 영상은 metadata만으로 부족할 수 있으니 미리 한번 Extract 결과를 같이 합쳐 입력 고정
        String prompt = "이 영상에서 언급되는 실제 장소를 찾아주세요.\n제목: "
                + nullSafe(metadata.title()) + "\n설명: " + nullSafe(metadata.description());
        SupadataExtractResponse submit = supadata.submitExtract(url, prompt);
        SupadataExtractResultResponse extract = null;
        for (int i = 0; i < 10; i++) {
            extract = supadata.getExtractResult(submit.jobId());
            if ("completed".equals(extract.status()) || "failed".equals(extract.status())) break;
            Thread.sleep(3000);
        }

        StringBuilder sb = new StringBuilder(metaContent);
        if (extract != null && "completed".equals(extract.status()) && extract.data() != null) {
            sb.append("\n추출 내용: ").append(extract.data());
        }
        return sb.toString();
    }

    private void printVideoStats(String label, List<Integer> counts, List<Set<String>> trials) {
        double mean = counts.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = counts.stream().mapToDouble(c -> Math.pow(c - mean, 2)).average().orElse(0);
        double std = Math.sqrt(variance);
        int min = counts.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = counts.stream().mapToInt(Integer::intValue).max().orElse(0);

        // Pairwise Jaccard
        double jaccardSum = 0;
        int pairs = 0;
        for (int i = 0; i < trials.size(); i++) {
            for (int j = i + 1; j < trials.size(); j++) {
                jaccardSum += jaccard(trials.get(i), trials.get(j));
                pairs++;
            }
        }
        double jaccardAvg = pairs > 0 ? jaccardSum / pairs : 0;

        // Intersection across all trials
        Set<String> union = new HashSet<>();
        trials.forEach(union::addAll);
        Set<String> intersection = new HashSet<>(trials.isEmpty() ? Set.of() : trials.get(0));
        for (int i = 1; i < trials.size(); i++) intersection.retainAll(trials.get(i));

        System.out.printf("%n[%s] count: mean=%.2f std=%.2f min=%d max=%d%n",
                label, mean, std, min, max);
        System.out.printf("[%s] Jaccard avg (pairwise) = %.3f%n", label, jaccardAvg);
        System.out.printf("[%s] stable set (∩ over all %d trials, n=%d): %s%n",
                label, trials.size(), intersection.size(), new TreeSet<>(intersection));
        System.out.printf("[%s] union set (n=%d): %s%n", label, union.size(), new TreeSet<>(union));
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> uni = new HashSet<>(a);
        uni.addAll(b);
        return uni.isEmpty() ? 0.0 : (double) inter.size() / uni.size();
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
}
