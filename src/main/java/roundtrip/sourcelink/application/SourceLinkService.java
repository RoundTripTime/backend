package roundtrip.sourcelink.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.parsing.domain.entity.ExtractionJob;
import roundtrip.parsing.domain.repository.ExtractionJobRepository;
import roundtrip.sourcelink.domain.entity.LinkStatus;
import roundtrip.sourcelink.domain.entity.SourceLink;
import roundtrip.sourcelink.domain.entity.SourceType;
import roundtrip.sourcelink.domain.repository.SourceLinkRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SourceLinkService {

    private final SourceLinkRepository sourceLinkRepository;
    private final ExtractionJobRepository extractionJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SubmitResult submit(UUID userId, String url) {
        SourceType sourceType = detectSourceType(url);
        if (sourceType == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PLATFORM);
        }

        String normalizedHash = normalizeHash(url);

        if (sourceLinkRepository.existsByUserIdAndNormalizedUrlHash(userId, normalizedHash)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LINK);
        }

        SourceLink sourceLink = SourceLink.create(userId, url, normalizedHash, sourceType);
        sourceLink = sourceLinkRepository.save(sourceLink);

        ExtractionJob job = ExtractionJob.create(sourceLink.getId());
        job = extractionJobRepository.save(job);

        // 트랜잭션 커밋 후 파이프라인 실행 (AFTER_COMMIT 이벤트)
        eventPublisher.publishEvent(new ExtractionTriggeredEvent(job.getId(), sourceLink.getId()));

        return new SubmitResult(sourceLink, job);
    }

    @Transactional(readOnly = true)
    public List<SourceLinkWithJob> listByUser(UUID userId, LinkStatus status, int limit, UUID cursor) {
        int effectiveLimit = (limit <= 0 || limit > 100) ? 20 : limit;
        List<SourceLink> links = sourceLinkRepository.findByUserIdWithCursor(userId, status, effectiveLimit, cursor);
        return links.stream().map(link -> {
            String jobStatus = extractionJobRepository.findBySourceLinkId(link.getId())
                    .map(job -> job.getJobStatus().name().toLowerCase())
                    .orElse(null);
            return new SourceLinkWithJob(link, jobStatus);
        }).toList();
    }

    private SourceType detectSourceType(String url) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        if (lower.contains("youtube.com/shorts") || lower.contains("youtu.be")) {
            return SourceType.YOUTUBE_SHORT;
        }
        if (lower.contains("instagram.com/reel") || lower.contains("instagram.com/p/")) {
            return SourceType.INSTAGRAM_REEL;
        }
        return null;
    }

    private String normalizeHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record SubmitResult(SourceLink sourceLink, ExtractionJob job) {}

    public record SourceLinkWithJob(SourceLink sourceLink, String jobStatus) {}
}
