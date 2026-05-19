package roundtrip.extract.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.extract.domain.entity.ExtractionJob;
import roundtrip.extract.domain.repository.ExtractionJobRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtractionJobService {

    private final ExtractionJobRepository extractionJobRepository;

    @Transactional(readOnly = true)
    public ExtractionJob getJob(UUID jobId) {
        return extractionJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
    }
}
