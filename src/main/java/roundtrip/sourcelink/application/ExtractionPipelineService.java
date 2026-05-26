package roundtrip.sourcelink.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.extract.domain.repository.ExtractionJobRepository;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.domain.repository.PlaceCandidateRepository;
import roundtrip.sourcelink.domain.entity.SourceLink;
import roundtrip.sourcelink.domain.repository.SourceLinkRepository;
import roundtrip.sourcelink.infrastructure.external.*;
import tools.jackson.databind.ObjectMapper;

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
    private final FeatherlessAiClient featherlessAiClient;
    private final KakaoLocalClient kakaoLocalClient;
    private final ObjectMapper objectMapper;

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
            SupadataMetadataResponse metadata = supadataClient.fetchMetadata(sourceLink.getUrl());
            updateSourceLinkProcessing(sourceLink, metadata);

            String metadataContent = buildMetadataContent(metadata);
            List<PlaceParseResult> phase1Result = featherlessAiClient.parsePlaces(metadataContent);

            if (!phase1Result.isEmpty()) {
                List<PlaceCandidate> candidates = normalizePlaces(phase1Result, job);
                completeJob(job, sourceLink, candidates.size());
            } else {
                String prompt = buildExtractPrompt(metadata);
                SupadataExtractResponse extractResponse = supadataClient.submitExtract(sourceLink.getUrl(), prompt);
                SupadataExtractResultResponse extractResult = pollExtractResult(extractResponse.jobId());

                if ("failed".equals(extractResult.status())) {
                    failJob(job, sourceLink, "EXTRACTION_FAILED");
                    return;
                }

                String fullContent = buildFullContent(metadata, extractResult);
                List<PlaceParseResult> phase2Result = featherlessAiClient.parsePlaces(fullContent);

                if (!phase2Result.isEmpty()) {
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

    @Transactional
    protected List<PlaceCandidate> normalizePlaces(List<PlaceParseResult> results, ExtractionJob job) {
        List<PlaceCandidate> candidates = new ArrayList<>();
        int rank = 0;

        for (PlaceParseResult result : results) {
            boolean requiresConfirmation = result.confidence() < 0.7;
            String providerMatchJson = null;

            List<KakaoLocalDocument> kakaoResults = kakaoLocalClient.searchByKeyword(result.name());
            if (!kakaoResults.isEmpty()) {
                KakaoLocalDocument topMatch = kakaoResults.get(0);
                try {
                    providerMatchJson = objectMapper.writeValueAsString(topMatch);
                } catch (Exception e) {
                    log.warn("Failed to serialize Kakao match for place={}: {}", result.name(), e.getMessage());
                }
            } else {
                requiresConfirmation = true;
            }

            PlaceCandidate candidate = PlaceCandidate.create(
                    job.getId(),
                    null,
                    result.name(),
                    result.category(),
                    BigDecimal.valueOf(result.confidence()),
                    rank++,
                    requiresConfirmation,
                    result.evidence(),
                    providerMatchJson
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
