package roundtrip.sourcelink.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import roundtrip.parsing.domain.entity.ExtractionJob;
import roundtrip.parsing.domain.repository.ExtractionJobRepository;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.domain.repository.PlaceCandidateRepository;
import roundtrip.sourcelink.domain.entity.SourceLink;
import roundtrip.sourcelink.domain.repository.SourceLinkRepository;
import roundtrip.sourcelink.infrastructure.external.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionPipelineService {

    private final ExtractionJobRepository extractionJobRepository;
    private final SourceLinkRepository sourceLinkRepository;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final SupadataClient supadataClient;
    private final GeminiClient geminiClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExtractionTriggered(ExtractionTriggeredEvent event) {
        UUID jobId = event.jobId();
        SourceLink sourceLink = sourceLinkRepository.findById(event.sourceLinkId())
                .orElseThrow(() -> new IllegalStateException("SourceLink not found: " + event.sourceLinkId()));
        runPipeline(jobId, sourceLink);
    }

    private void runPipeline(UUID jobId, SourceLink sourceLink) {
        ExtractionJob job = extractionJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        startJob(job);

        try {
            // Phase 1: Supadata Metadata
            SupadataMetadataResponse metadata = supadataClient.fetchMetadata(sourceLink.getUrl());
            updateSourceLinkProcessing(sourceLink, metadata);

            // Phase 1b: Gemini on metadata
            String metadataContent = buildMetadataContent(metadata);
            List<GeminiPlaceParseResult> phase1Result = geminiClient.parsePlaces(metadataContent);

            if (!phase1Result.isEmpty() && avgConfidence(phase1Result) > 0.7) {
                List<PlaceCandidate> candidates = normalizePlaces(phase1Result, job);
                completeJob(job, sourceLink, candidates.size());
            } else {
                // Phase 2: Supadata Extract
                String prompt = buildExtractPrompt(metadata);
                SupadataExtractResponse extractResponse = supadataClient.submitExtract(sourceLink.getUrl(), prompt);

                SupadataExtractResultResponse extractResult = pollExtractResult(extractResponse.jobId());

                if ("failed".equals(extractResult.status())) {
                    failJob(job, sourceLink, "EXTRACTION_FAILED");
                    return;
                }

                // Phase 2b: Gemini on full extract
                String fullContent = buildFullContent(metadata, extractResult);
                List<GeminiPlaceParseResult> phase2Result = geminiClient.parsePlaces(fullContent);

                if (!phase2Result.isEmpty() && phase2Result.stream().anyMatch(r -> r.confidence() > 0.5)) {
                    List<PlaceCandidate> candidates = normalizePlaces(phase2Result, job);
                    completeJob(job, sourceLink, candidates.size());
                } else {
                    failJob(job, sourceLink, "NO_PLACES_FOUND");
                }
            }

            // TODO: 알림 전송 (Notification 도메인 구현 후 추가 예정)

        } catch (Exception e) {
            log.error("Pipeline failed for jobId={}, sourceLinkId={}", jobId, sourceLink.getId(), e);
            failJob(job, sourceLink, "INTERNAL_ERROR");
        }
    }

    @Transactional
    protected void startJob(ExtractionJob job) {
        job.start();
        extractionJobRepository.save(job);
    }

    @Transactional
    protected void updateSourceLinkProcessing(SourceLink sourceLink, SupadataMetadataResponse metadata) {
        sourceLink.markProcessing();
        sourceLinkRepository.save(sourceLink);
    }

    @Transactional
    protected void completeJob(ExtractionJob job, SourceLink sourceLink, int signalCount) {
        job.complete(signalCount);
        sourceLink.markDone(null, null);
        extractionJobRepository.save(job);
        sourceLinkRepository.save(sourceLink);
    }

    @Transactional
    protected void failJob(ExtractionJob job, SourceLink sourceLink, String errorCode) {
        job.fail(errorCode);
        sourceLink.markFailed();
        extractionJobRepository.save(job);
        sourceLinkRepository.save(sourceLink);
    }

    private String buildMetadataContent(SupadataMetadataResponse metadata) {
        return "제목: " + nullSafe(metadata.title()) +
               "\n설명: " + nullSafe(metadata.description()) +
               "\n태그: " + (metadata.tags() != null ? String.join(", ", metadata.tags()) : "");
    }

    private String buildExtractPrompt(SupadataMetadataResponse metadata) {
        return "이 영상에서 언급되는 실제 장소를 찾아주세요. " +
               "우선순위: 1) 영상 메타데이터(제목/설명) 2) 자막/캡션 3) 기타 시각적 정보\n" +
               "영상 메타데이터:\n제목: " + nullSafe(metadata.title()) +
               "\n설명: " + nullSafe(metadata.description());
    }

    private String buildFullContent(SupadataMetadataResponse metadata, SupadataExtractResultResponse extractResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(nullSafe(metadata.title())).append("\n");
        sb.append("설명: ").append(nullSafe(metadata.description())).append("\n");
        if (extractResult.data() != null) {
            sb.append("추출 내용: ").append(extractResult.data().toString());
        }
        return sb.toString();
    }

    private double avgConfidence(List<GeminiPlaceParseResult> results) {
        return results.stream()
                .mapToDouble(GeminiPlaceParseResult::confidence)
                .average()
                .orElse(0.0);
    }

    // 지도 정규화 (Kakao/Google Maps API) — 추후 구현 예정
    @Transactional
    protected List<PlaceCandidate> normalizePlaces(List<GeminiPlaceParseResult> results, ExtractionJob job) {
        List<PlaceCandidate> candidates = new ArrayList<>();
        int rank = 0;

        for (GeminiPlaceParseResult result : results) {
            // TODO: Kakao Maps API 연동 후 place_id 채우기
            boolean requiresConfirmation = result.confidence() < 0.7;

            PlaceCandidate candidate = PlaceCandidate.create(
                    job.getId(),
                    null,          // place_id: 지도 정규화 전 null
                    result.name(),
                    result.category(),
                    BigDecimal.valueOf(result.confidence()),
                    rank++,
                    requiresConfirmation,
                    result.evidence(),
                    null           // providerMatchJson: 지도 정규화 전 null
            );
            candidates.add(candidate);
        }

        return placeCandidateRepository.saveAll(candidates);
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private SupadataExtractResultResponse pollExtractResult(String extractJobId) throws InterruptedException {
        int maxAttempts = 10;
        int intervalMs = 3000;

        for (int i = 0; i < maxAttempts; i++) {
            SupadataExtractResultResponse result = supadataClient.getExtractResult(extractJobId);
            if ("completed".equals(result.status()) || "failed".equals(result.status())) {
                return result;
            }
            Thread.sleep(intervalMs);
        }

        return new SupadataExtractResultResponse("failed", null, "POLL_TIMEOUT");
    }
}
