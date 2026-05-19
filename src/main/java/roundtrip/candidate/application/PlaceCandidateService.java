package roundtrip.candidate.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.parsing.domain.entity.ExtractionJob;
import roundtrip.parsing.domain.repository.ExtractionJobRepository;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.candidate.domain.entity.CandidateStatus;
import roundtrip.candidate.domain.entity.PlaceCandidate;
import roundtrip.candidate.domain.repository.PlaceCandidateRepository;
import roundtrip.sourcelink.domain.entity.SourceLink;
import roundtrip.sourcelink.domain.repository.SourceLinkRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaceCandidateService {

    private final PlaceCandidateRepository placeCandidateRepository;
    private final ExtractionJobRepository extractionJobRepository;
    private final SourceLinkRepository sourceLinkRepository;
    private final PlaceRepository placeRepository;

    @Transactional(readOnly = true)
    public CandidateListResult getCandidatesByJob(UUID jobId) {
        ExtractionJob job = extractionJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));

        SourceLink sourceLink = sourceLinkRepository.findById(job.getSourceLinkId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_LINK_NOT_FOUND));

        List<PlaceCandidate> candidates = placeCandidateRepository.findByJobId(jobId);

        List<CandidateWithPlace> candidateWithPlaces = candidates.stream()
                .map(c -> {
                    Place place = c.getPlaceId() != null
                            ? placeRepository.findById(c.getPlaceId()).orElse(null)
                            : null;
                    return new CandidateWithPlace(c, place);
                })
                .toList();

        return new CandidateListResult(sourceLink, candidateWithPlaces);
    }

    @Transactional
    public PlaceCandidate updateCandidate(UUID candidateId, CandidateStatus status) {
        PlaceCandidate candidate = placeCandidateRepository.findById(candidateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));

        if (candidate.getStatus() != CandidateStatus.PROPOSED) {
            throw new BusinessException(ErrorCode.ALREADY_PROCESSED_CANDIDATE);
        }

        switch (status) {
            case ACCEPTED -> candidate.accept();
            case REJECTED -> candidate.reject();
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid status: " + status);
        }

        return placeCandidateRepository.save(candidate);
    }

    @Transactional
    public BatchUpdateResult batchUpdateCandidates(List<BatchUpdateRequest> requests) {
        List<UUID> updated = new ArrayList<>();
        List<FailedUpdate> failed = new ArrayList<>();

        for (BatchUpdateRequest request : requests) {
            try {
                PlaceCandidate candidate = placeCandidateRepository.findById(request.candidateId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));

                if (candidate.getStatus() != CandidateStatus.PROPOSED) {
                    throw new BusinessException(ErrorCode.ALREADY_PROCESSED_CANDIDATE);
                }

                switch (request.status()) {
                    case ACCEPTED -> candidate.accept();
                    case REJECTED -> candidate.reject();
                    default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid status: " + request.status());
                }

                placeCandidateRepository.save(candidate);
                updated.add(request.candidateId());
            } catch (BusinessException e) {
                failed.add(new FailedUpdate(request.candidateId(), e.getCode()));
            }
        }

        return new BatchUpdateResult(updated, failed);
    }

    public record CandidateListResult(SourceLink sourceLink, List<CandidateWithPlace> candidates) {}

    public record CandidateWithPlace(PlaceCandidate candidate, Place place) {}

    public record BatchUpdateRequest(UUID candidateId, CandidateStatus status) {}

    public record BatchUpdateResult(List<UUID> updated, List<FailedUpdate> failed) {}

    public record FailedUpdate(UUID candidateId, String reason) {}
}
